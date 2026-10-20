package com.julz.sessionstats.client;

import com.julz.sessionstats.core.Format;
import com.julz.sessionstats.core.SessionTotals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.Locale;

/**
 * The shared look: dark translucent panels, one accent color, large numbers and small labels,
 * so the summary reads well in a screenshot or on stream.
 */
final class Ui {
    static final int PANEL = 0xD0101317;
    static final int PANEL_BORDER = 0x26FFFFFF;
    static final int ACCENT = 0xFF5EEAD4;
    static final int TEXT = 0xFFF2F4F5;
    static final int MUTED = 0xFF98A1AB;
    static final int RECORDING = 0xFFFF5C5C;
    static final int PAUSED = 0xFFFFC14D;

    private Ui() {
    }

    static void open(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.outline(x, y, width, height, PANEL_BORDER);
    }

    /** A panel with the background at {@code opacity} percent; nothing at 0. */
    static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int opacity) {
        if (opacity <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + height, withAlpha(PANEL, (PANEL >>> 24) * opacity / 100));
        graphics.outline(x, y, width, height, withAlpha(PANEL_BORDER, (PANEL_BORDER >>> 24) * opacity / 100));
    }

    static int withAlpha(int argb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (argb & 0xFFFFFF);
    }

    /** Text drawn larger than the font, for the big numbers. */
    static void scaledText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    /** Shortens text with "..." to fit a width. */
    static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    static Component statName(SessionTotals.Stat stat) {
        return Component.translatable("session-stats.stat." + stat.name().toLowerCase(Locale.ROOT));
    }

    static String statValue(SessionTotals totals, SessionTotals.Stat stat) {
        return Format.stat(stat, totals.get(stat));
    }

    /** The block for an id such as {@code minecraft:stone}, or null for an unknown id. */
    static Block block(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || !BuiltInRegistries.BLOCK.containsKey(identifier)) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getValue(identifier);
    }

    static Component blockName(String id) {
        Block block = block(id);
        return block != null ? block.getName() : Component.literal(id);
    }

    static ItemStack blockIcon(String id) {
        Block block = block(id);
        return block != null ? new ItemStack(block) : ItemStack.EMPTY;
    }
}
