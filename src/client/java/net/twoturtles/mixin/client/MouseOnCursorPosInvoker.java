package net.twoturtles.mixin.client;

import net.minecraft.client.Mouse;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;


@Mixin(Mouse.class)
public interface MouseOnCursorPosInvoker {
    @Invoker("onCursorPos")
    public void invokeOnCursorPos(long window, double x, double y);
}