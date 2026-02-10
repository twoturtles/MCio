package net.twoturtles.mixin;

import net.minecraft.server.ServerTickRateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerTickRateManager.class)
public interface ServerTickRateManagerAccessor {
  @Accessor("remainingSprintTicks")
  void setRemainingSprintTicks(long ticks);

  @Accessor("scheduledCurrentSprintTicks")
  void setScheduledCurrentSprintTicks(long ticks);
}
