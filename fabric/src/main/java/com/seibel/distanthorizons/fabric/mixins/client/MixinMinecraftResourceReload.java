package com.seibel.distanthorizons.fabric.mixins.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

#if MC_VER == MC_1_20_4
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
#endif

/**
 * リソースパックの読み込み直し（サーバーのリソースパックの適用、F3+T、ミップマップの段数の変更など）が終わったら、
 * ブロックの色の計算と LOD の描画データを作り直す（kabu.16〜）。
 *
 * <p>読み込み直しの最中は、テクスチャの画像（atlas）が解放されている。そのあいだに LOD の色を計算すると
 * 「Image is not allocated」で失敗し、そのブロックは真っピンク（ColorUtil.HOT_PINK）のまま覚えられて、
 * ワールドを入り直すまで LOD がピンクになっていた。読み込み直しが終わってから捨てて計算し直せば元の色に戻る。</p>
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftResourceReload
{
	#if MC_VER == MC_1_20_4
	@Unique
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();

	@Inject(method = "reloadResourcePacks(ZLnet/minecraft/client/Minecraft$GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"))
	private void kabunushi$clearLodColorsAfterReload(CallbackInfoReturnable<CompletableFuture<Void>> cir)
	{
		CompletableFuture<Void> future = cir.getReturnValue();
		if (future == null)
		{
			return;
		}

		Minecraft minecraft = (Minecraft) (Object) this;
		future.thenRunAsync(() ->
		{
			try
			{
				if (DhApi.Delayed.renderProxy != null && minecraft.level != null)
				{
					DhApi.Delayed.renderProxy.clearRenderDataCache();
					LOGGER.info("Resource reload finished, LOD colors and render data were rebuilt.");
				}
			}
			catch (Exception e)
			{
				LOGGER.warn("Unable to rebuild LOD colors after a resource reload: [" + e.getMessage() + "].");
			}
		}, minecraft);
	}
	#endif
}
