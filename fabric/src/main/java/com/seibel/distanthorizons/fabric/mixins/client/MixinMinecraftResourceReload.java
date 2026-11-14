package com.seibel.distanthorizons.fabric.mixins.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

#if MC_VER == MC_1_20_4
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.common.wrappers.block.ClientBlockStateColorCache;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
#endif

/**
 * リソースパックの読み込み直し（サーバーのリソースパックの適用、F3+T、ミップマップの段数の変更など）の最中に
 * LOD の色を計算して真っピンクになったときだけ、終わってから LOD を作り直す（kabu.16〜、kabu.17 で条件付きに）。
 *
 * <p>読み込み直しの最中は、テクスチャの画像（atlas）が解放されている。そのあいだに LOD の色を計算すると
 * 「Image is not allocated」で失敗し、そのブロックは真っピンク（ColorUtil.HOT_PINK）のまま覚えられて、
 * ワールドを入り直すまで LOD がピンクになっていた。</p>
 *
 * <p>kabu.16 では読み込み直しのたびに必ず作り直していたが、サーバーに入った直後（DH が LOD を読み込んでいる最中）にも
 * 作り直すことになり、その直後に JVM が GC の中で落ちる（EXCEPTION_ACCESS_VIOLATION）ことが 2 回続いた。
 * 作り直しは描画のデータ（ネイティブのメモリ）を捨てるので、読み込み中に捨てるのは危ない。そこで kabu.17 では、
 * ピンクになったことがあるときだけ、読み込み直しが終わって 5 秒おいてから作り直す。</p>
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftResourceReload
{
	#if MC_VER == MC_1_20_4
	@Unique
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();

	/** 読み込み直しが終わってから作り直すまでの待ち（秒） */
	@Unique
	private static final long REBUILD_DELAY_SECONDS = 5;

	@Inject(method = "reloadResourcePacks(ZLnet/minecraft/client/Minecraft$GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
	private void kabunushi$resetHotPinkFlag(CallbackInfoReturnable<CompletableFuture<Void>> cir)
	{
		ClientBlockStateColorCache.hotPinkSinceReload = false;
	}

	@Inject(method = "reloadResourcePacks(ZLnet/minecraft/client/Minecraft$GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"))
	private void kabunushi$clearLodColorsAfterReload(CallbackInfoReturnable<CompletableFuture<Void>> cir)
	{
		CompletableFuture<Void> future = cir.getReturnValue();
		if (future == null)
		{
			return;
		}

		Minecraft minecraft = (Minecraft) (Object) this;
		future.thenRun(() -> CompletableFuture.delayedExecutor(REBUILD_DELAY_SECONDS, TimeUnit.SECONDS).execute(() -> minecraft.execute(() ->
		{
			if (!ClientBlockStateColorCache.hotPinkSinceReload)
			{
				return;
			}
			ClientBlockStateColorCache.hotPinkSinceReload = false;

			try
			{
				if (DhApi.Delayed.renderProxy != null && minecraft.level != null)
				{
					DhApi.Delayed.renderProxy.clearRenderDataCache();
					LOGGER.info("Some LOD colors became hot pink during a resource reload, LOD colors and render data were rebuilt.");
				}
			}
			catch (Exception e)
			{
				LOGGER.warn("Unable to rebuild LOD colors after a resource reload: [" + e.getMessage() + "].");
			}
		})));
	}
	#endif
}
