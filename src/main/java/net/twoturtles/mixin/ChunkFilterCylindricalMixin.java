//package net.minecraft.server.network;
package net.twoturtles.mixin;

import java.util.function.Consumer;

import net.minecraft.server.network.ChunkFilter;
import net.minecraft.util.math.ChunkPos;
import net.twoturtles.ChunksDebug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkFilter.Cylindrical.class)
abstract public class ChunkFilterCylindricalMixin {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(
            "net.twoturtles.mixin.main.ChunkFilterCylindricalMixin");

    /* The recommended use of Shadow is to declare the class and methods abstract */
    @Shadow
    abstract int getLeft();
    @Shadow
    abstract int getRight();
    @Shadow
    abstract int getBottom();
    @Shadow
    abstract int getTop();

    @Inject(method = "forEach", at = @At("HEAD"))
    private void beforeForEachStart(Consumer<ChunkPos> consumer, CallbackInfo ci) {
        ChunkFilter.Cylindrical cyl = ((ChunkFilter.Cylindrical)(Object) this);
        LOGGER.info("Select-Chunks Started center={} range-x={}:{} range-z={}:{} viewDistance={}",
                cyl.center(), getLeft(), getRight(), getBottom(), getTop(), cyl.viewDistance());
        ChunksDebug.getInstance().selectStart();
    }

    @Inject(method = "forEach",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V")
    )
    private void beforeAcceptCall(Consumer<ChunkPos> consumer, CallbackInfo ci,
                                  @Local(ordinal = 0) int i, @Local(ordinal = 1) int j ) {
        ChunksDebug.getInstance().selectTrack(i, j);
    }

    @Inject(method = "forEach", at = @At("TAIL"))
    private void afterForEachEnd(Consumer<ChunkPos> consumer, CallbackInfo ci) {
        ChunksDebug.getInstance().selectEnd();
    }

}