package net.twoturtles.mixin;

import net.minecraft.server.ServerTickManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerTickManager.class)
public interface ServerTickManagerAccessor {
  @Accessor("sprintTicks")
  void setSprintTicks(long ticks);

  @Accessor("scheduledSprintTicks")
  void setScheduledSprintTicks(long ticks);
}
