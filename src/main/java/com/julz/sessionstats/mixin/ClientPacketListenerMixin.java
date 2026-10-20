package com.julz.sessionstats.mixin;

import com.julz.sessionstats.client.SessionController;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The answer to a stats request. The handler first hands the packet over to the main thread, so
 * the end of the method is only reached there.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleAwardStats", at = @At("TAIL"))
    private void sessionStats$onStats(ClientboundAwardStatsPacket packet, CallbackInfo ci) {
        SessionController.onStatsPacket(packet.stats());
    }
}
