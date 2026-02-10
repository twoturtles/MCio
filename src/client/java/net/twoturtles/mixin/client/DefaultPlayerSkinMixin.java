package net.twoturtles.mixin.client;

import java.util.UUID;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
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

@Mixin(DefaultPlayerSkin.class)
public class DefaultPlayerSkinMixin {
  @Unique
  private static final Logger LOGGER =
      LoggerFactory.getLogger("net.twoturtles.mixin.client.DefaultSkinHelperMixin");

  @Final @Shadow private static PlayerSkin[] DEFAULT_SKINS;

  /** Provides access to the list of default skins. */
  @Mixin(DefaultPlayerSkin.class)
  public interface SkinAccessor {
    @Accessor("DEFAULT_SKINS")
    static PlayerSkin[] getSkins() {
      throw new AssertionError(); /* Dummy method */
    }
  }

  /** Intercept the call to get a default skin and return the configured skin. */
  @Inject(
      method = "get(Ljava/util/UUID;)Lnet/minecraft/client/resources/PlayerSkin;",
      at = @At("HEAD"),
      cancellable = true)
  private static void onGetSkinTextures(UUID uuid, CallbackInfoReturnable<PlayerSkin> cir) {
    int skin_ix = MCioConfig.getInstance().skin;
    if (skin_ix < 0 || skin_ix >= DEFAULT_SKINS.length) {
      LOGGER.warn("InvalidSkin {}", skin_ix);
      skin_ix = MCioConfig.DEFAULT_MCIO_SKIN;
    }
    cir.setReturnValue(DEFAULT_SKINS[skin_ix]);
  }
}
