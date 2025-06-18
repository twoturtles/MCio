package net.twoturtles.mixin.client;

import java.util.UUID;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.twoturtles.MCioConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DefaultSkinHelper.class)
public class DefaultSkinHelperMixin {
  @Unique
  private static final Logger LOGGER =
      LoggerFactory.getLogger("net.twoturtles.mixin.client.DefaultSkinHelperMixin");

  @Final @Shadow private static SkinTextures[] SKINS;

  /** Provides access to the list of default skins. */
  @Mixin(DefaultSkinHelper.class)
  public interface SkinAccessor {
    @Accessor("SKINS")
    static SkinTextures[] getSkins() {
      throw new AssertionError(); /* Dummy method */
    }
  }

  /** Intercept the call to get a default skin and return the configured skin. */
  @Inject(
      method = "getSkinTextures(Ljava/util/UUID;)Lnet/minecraft/client/util/SkinTextures;",
      at = @At("HEAD"),
      cancellable = true)
  private static void onGetSkinTextures(UUID uuid, CallbackInfoReturnable<SkinTextures> cir) {
    int skin_ix = MCioConfig.getInstance().skin;
    if (skin_ix < 0 || skin_ix >= SKINS.length) {
      LOGGER.warn("InvalidSkin {}", skin_ix);
      skin_ix = MCioConfig.DEFAULT_MCIO_SKIN;
    }
    cir.setReturnValue(SKINS[skin_ix]);
  }
}
