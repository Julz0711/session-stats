package com.julz.sessionstats.client;

import com.julz.sessionstats.config.ModConfig;
import com.julz.sessionstats.core.Format;
import com.julz.sessionstats.core.Session;
import com.julz.sessionstats.core.SessionRecorder;
import com.julz.sessionstats.core.SessionTotals;
import com.julz.sessionstats.core.StatKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The small overlay: a recording dot, the timer and the stats the player picked. */
final class SessionHud {
    private static final int PADDING = 5;
    private static final int LINE_HEIGHT = 11;
    private static final int GAP = 10;
    private static final int DOT = 5;
    private static final int ICON = 9;

    private SessionHud() {
    }

    /** Where the HUD is drawn on screen, in GUI pixels after scaling. */
    record Bounds(int x, int y, int width, int height) {
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    /** One row: an optional block icon, a label, the value and its rate per hour (or null). */
    private record Line(ItemStack icon, String label, String value, String rate) {
    }

    static void render(GuiGraphicsExtractor graphics, Minecraft client) {
        SessionRecorder recorder = SessionController.recorder();
        ModConfig config = ModConfig.getInstance();
        if (!config.showHud || !recorder.isActive() || client.player == null) {
            return;
        }
        Session session = recorder.current();
        draw(graphics, client.font, session, recorder.state() == SessionRecorder.State.PAUSED, config);
    }

    /** Draws the HUD for a session, also used by the HUD editor's preview. Returns where it was drawn. */
    static Bounds draw(GuiGraphicsExtractor graphics, Font font, Session session, boolean paused, ModConfig config) {
        SessionTotals totals = session.totals();
        List<Line> lines = lines(totals, config);
        Component header = paused
                ? Component.translatable("hud.session-stats.paused")
                : Component.translatable("hud.session-stats.recording");
        String timer = Format.timer(session.activeMillis);

        // Column widths: labels on the left, values and rates right-aligned
        int labelWidth = 0;
        int valueWidth = 0;
        int rateWidth = 0;
        for (Line line : lines) {
            labelWidth = Math.max(labelWidth, (line.icon().isEmpty() ? 0 : ICON + 3) + font.width(line.label()));
            valueWidth = Math.max(valueWidth, font.width(line.value()));
            if (line.rate() != null) {
                rateWidth = Math.max(rateWidth, font.width(line.rate()));
            }
        }
        int rowsWidth = lines.isEmpty() ? 0 : labelWidth + GAP + valueWidth + (rateWidth > 0 ? 6 + rateWidth : 0);
        int headerWidth = config.hudShowTimer ? DOT + 4 + font.width(header) + GAP + font.width(timer) : 0;
        int width = Math.max(rowsWidth, headerWidth) + PADDING * 2;
        int rows = lines.size() + (config.hudShowTimer ? 1 : 0);
        int height = PADDING * 2 + LINE_HEIGHT * rows - 2 + (config.hudShowTimer && !lines.isEmpty() ? 2 : 0);

        float scale = config.hudScale / 100f;
        int scaledWidth = Math.round(width * scale);
        int scaledHeight = Math.round(height * scale);
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        ModConfig.Corner corner = config.hudCorner;
        int x = corner.right() ? screenWidth - scaledWidth - config.hudOffsetX : config.hudOffsetX;
        int y = corner.bottom() ? screenHeight - scaledHeight - config.hudOffsetY : config.hudOffsetY;
        x = Math.clamp(x, 0, Math.max(0, screenWidth - scaledWidth));
        y = Math.clamp(y, 0, Math.max(0, screenHeight - scaledHeight));

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        Ui.panel(graphics, 0, 0, width, height, config.hudOpacity);

        int color = config.hudColor.argb;
        boolean shadow = config.hudTextShadow;
        int textY = PADDING;
        if (config.hudShowTimer) {
            int dotColor = paused ? Ui.PAUSED : Ui.RECORDING;
            graphics.fill(PADDING, textY + 1, PADDING + DOT, textY + 1 + DOT, dotColor);
            graphics.text(font, header, PADDING + DOT + 4, textY, dotColor, shadow);
            graphics.text(font, timer, width - PADDING - font.width(timer), textY, Ui.TEXT, shadow);
            textY += LINE_HEIGHT + 2;
        } else if (!lines.isEmpty()) {
            // Without the header there is nothing else showing that recording is on, so keep the dot
            graphics.fill(width - 3, 1, width - 1, 3, paused ? Ui.PAUSED : Ui.RECORDING);
        }

        int rateX = width - PADDING - rateWidth;
        int valueRight = rateWidth > 0 ? rateX - 6 : width - PADDING;
        for (Line line : lines) {
            int labelX = PADDING;
            if (!line.icon().isEmpty()) {
                drawIcon(graphics, line.icon(), labelX, textY - 1);
                labelX += ICON + 3;
            }
            graphics.text(font, line.label(), labelX, textY, Ui.MUTED, shadow);
            graphics.text(font, line.value(), valueRight - font.width(line.value()), textY, color, shadow);
            if (line.rate() != null) {
                graphics.text(font, line.rate(), width - PADDING - font.width(line.rate()), textY, Ui.MUTED, shadow);
            }
            textY += LINE_HEIGHT;
        }
        graphics.pose().popMatrix();
        return new Bounds(x, y, scaledWidth, scaledHeight);
    }

    private static List<Line> lines(SessionTotals totals, ModConfig config) {
        List<Line> lines = new ArrayList<>();
        for (SessionTotals.Stat stat : config.hudStats()) {
            long value = totals.get(stat);
            String rate = config.hudShowRates ? rate(totals, stat, value) : null;
            lines.add(new Line(ItemStack.EMPTY, Ui.statName(stat).getString(), Format.stat(stat, value), rate));
            if (stat == SessionTotals.Stat.ORES_MINED && config.hudShowOres) {
                for (Map.Entry<String, Long> ore : totals.ores().entrySet()) {
                    String block = StatKeys.oreBlock(ore.getKey());
                    String oreRate = config.hudShowRates ? rate(totals, stat, ore.getValue()) : null;
                    lines.add(new Line(Ui.blockIcon(block), Ui.blockName(block).getString(),
                            Format.number(ore.getValue()), oreRate));
                }
            }
        }
        return lines;
    }

    private static String rate(SessionTotals totals, SessionTotals.Stat stat, long value) {
        double perHour = totals.perHour(value);
        return Component.translatable("hud.session-stats.per_hour", Format.statRate(stat, perHour)).getString();
    }

    /** A block icon shrunk to the line height. */
    private static void drawIcon(GuiGraphicsExtractor graphics, ItemStack icon, int x, int y) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(ICON / 16f, ICON / 16f);
        graphics.item(icon, 0, 0);
        graphics.pose().popMatrix();
    }
}
