package net.twoturtles.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.registry.Registries;
import net.minecraft.stat.StatHandler;
import net.minecraft.stat.StatType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;

import net.minecraft.stat.Stat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.entity.player.PlayerEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.twoturtles.MCioStats;

@Mixin(ServerStatHandler.class)
abstract public class ServerStatHandlerMixin {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(
            "net.twoturtles.mixin.ServerStatHandlerMixin");

    @Mixin(StatHandler.class)
    public interface StatHandlerAccessor {
        @Accessor("statMap")
        Object2IntMap<Stat<?>> getStatMap();
    }

    @Mixin(ServerStatHandler.class)
    public interface asStringInvoker {
        @Invoker("asString")
        public String invokeAsString();
    }

    @Inject(method = "setStat", at = @At("HEAD"))
    private void injectSetStatHead(PlayerEntity player, Stat<?> stat, int value, CallbackInfo ci) {
        MCioStats.getInstance().updateStats((ServerStatHandler) (Object) this, stat, value);
    }

    /* This targets the ServerStatHandler constructor. This needs to run at the start, but you
     * can't target HEAD of init. Instead, targeting the first assignment.
     * XXX Seems brittle
     */
    @Inject(method = "<init>",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/stat/ServerStatHandler;server:Lnet/minecraft/server/MinecraftServer;",
                    shift = At.Shift.AFTER))
    private void onInit(MinecraftServer server, File file, CallbackInfo ci) {
        MCioStats.getInstance().onServerStatHandlerInit((ServerStatHandler) (Object) this);
    }

    @Inject(method = "parse", at = @At("RETURN"))
    private void onParseReturn(CallbackInfo ci) {
        MCioStats.getInstance().replaceStats(
                (ServerStatHandler) (Object) this,
                ((StatHandlerAccessor)(Object)this).getStatMap()
        );
    }
}