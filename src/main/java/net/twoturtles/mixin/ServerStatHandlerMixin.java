package net.twoturtles.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.stat.ServerStatHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerStatHandler.class)
public class ServerStatHandlerMixin {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(
            "net.twoturtles.mixin.ServerStatHandlerMixin");

    @Mixin(ServerStatHandler.class)
    public interface asStringInvoker {
        @Invoker("asString")
        public String invokeAsString();
    }
}
