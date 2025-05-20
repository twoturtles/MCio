package net.twoturtles.mixin;

import java.util.function.Consumer;


import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.math.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.network.ChunkFilter$Cylindrical")
public class ChunkFilterCylindricalMixin {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("net.twoturtles.mixin.main.ChunkFilterCylindricalMixin");
    @Unique
    private static boolean runningForEach = false;

    @Inject(method = "forEach", at = @At("HEAD"))
    private void beforeForEachStart(Consumer<ChunkPos> consumer, CallbackInfo ci) {
        runningForEach = true;
        LOGGER.info("forEach Started");
    }

    @Inject(method = "forEach", at = @At("TAIL"))
    private void afterForEachEnd(Consumer<ChunkPos> consumer, CallbackInfo ci) {
        runningForEach = false;
        LOGGER.info("forEach Ended");
    }

    @Inject(method = "forEach", at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
    private void beforeAcceptCall(Consumer<ChunkPos> consumer, CallbackInfo ci,
                                  @Local(ordinal = 0) int i, @Local(ordinal = 1) int j ) {
        System.out.println("[Mixin] Before accepting chunk");
        LOGGER.info("AddChunk {} {}", i, j);
    }
}