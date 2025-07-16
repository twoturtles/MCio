package net.twoturtles.mixin;

import net.minecraft.util.math.random.RandomSeed;
import net.twoturtles.MCioUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RandomSeed.class)
public class RandomSeedMixin {
  @Unique
  private static final Logger LOGGER =
      LoggerFactory.getLogger("net.twoturtles.mixin.RandomSeedMixin");

  @Unique private static long calls = 0;
  @Unique private static MCioUtil.StackTraceCounter stacks = new MCioUtil.StackTraceCounter();

  @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
  private static void injectGetSeed(CallbackInfoReturnable<Long> cir) {
    //    stacks.record(3, 4); // callers of RandomSeed.getSeed()
    stacks.record(3, 5); //
    if (calls == 20000) {
      stacks.printStats();
      MCioUtil.hardExit(0);
    }
    //    cir.setReturnValue(seed++);
    calls++;
    if (calls % 1000 == 0) {
      MCioUtil.stdout.printf("======== Calls %d\n", calls);
    }
  }
}
