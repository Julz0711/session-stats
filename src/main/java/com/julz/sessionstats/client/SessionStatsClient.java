package com.julz.sessionstats.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionStatsClient implements ClientModInitializer {
    public static final String MOD_ID = "session-stats";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "general"));

    private static KeyMapping recordKey;
    private static KeyMapping pauseKey;
    private static KeyMapping historyKey;

    @Override
    public void onInitializeClient() {
        recordKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.session-stats.record", InputConstants.Type.KEYBOARD, InputConstants.KEY_F7, CATEGORY));
        pauseKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.session-stats.pause", InputConstants.Type.KEYBOARD, InputConstants.KEY_F8, CATEGORY));
        // Unbound by default; the history is also in the settings screen.
        historyKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.session-stats.history", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (recordKey.consumeClick()) {
                SessionController.toggleRecording(client);
            }
            while (pauseKey.consumeClick()) {
                SessionController.togglePause(client);
            }
            while (historyKey.consumeClick()) {
                Ui.open(new HistoryScreen(null));
            }
            SessionController.tick(client);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> SessionController.onJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SessionController.onDisconnect());
        // Closing the game window while recording still finishes and saves the session
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SessionController.onDisconnect());
        ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> SessionController.onBlockBroken(state));

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
                (graphics, deltaTracker) -> SessionHud.render(graphics, Minecraft.getInstance()));
    }
}
