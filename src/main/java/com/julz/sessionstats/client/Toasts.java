package com.julz.sessionstats.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

final class Toasts {
    private static final SystemToast.SystemToastId ID = new SystemToast.SystemToastId();

    private Toasts() {
    }

    static void show(Minecraft client, Component title, Component message) {
        SystemToast.addOrUpdate(client.gui.toastManager(), ID, title, message);
    }
}
