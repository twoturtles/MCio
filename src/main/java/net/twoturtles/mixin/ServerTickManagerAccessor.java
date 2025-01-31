package net.twoturtles.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.server.ServerTickManager;


@Mixin(ServerTickManager.class)
public interface ServerTickManagerAccessor {
    @Accessor("sprintTicks")
    void setSprintTicks(long ticks);

    @Accessor("scheduledSprintTicks")
    void setScheduledSprintTicks(long ticks);
}
