package com.julz.sessionstats.client;

import com.julz.sessionstats.config.ModConfig;
import com.julz.sessionstats.core.Format;
import com.julz.sessionstats.core.Session;
import com.julz.sessionstats.core.SessionRecorder;
import com.julz.sessionstats.core.SessionTotals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The small overlay: a recording dot, the timer and the stats the player picked. */
final class SessionHud {
    private static final int PADDING = 5;
    private static final int LINE_HEIGHT = 11;
    private static final int GAP = 12;
    private static final int DOT = 5;

    private SessionHud() {
    }

    /** Where the HUD is drawn on screen. */
    record Bounds(int x, int y, int width, int height) {
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private record Line(Component label, String value) {
    }

    static void render(GuiGraphicsExtractor graphics, Minecraft client) {
        SessionRecorder recorder = SessionController.recorder();
        ModConfig config = ModConfig.getInstance();
        if (!config.showHud || !recorder.isActive() || client.player == null || client.options.hideGui) {
            return;
        }
        Session session = recorder.current();
        draw(graphics, client.font, session, recorder.state() == SessionRecorder.State.PAUSED, config);
    }

    /** Draws the HUD for a session, also used by the HUD editor's preview. Returns where it was drawn. */
    static Bounds draw(GuiGraphicsExtractor graphics, Font font, Session session, boolean paused, ModConfig config) {
        SessionTotals totals = session.totals();
        List<Line> lines = new ArrayList<>();
        for (SessionTotals.Stat stat : config.hudStats()) {
            lines.add(new Line(Ui.statName(stat), Ui.statValue(totals, stat)));
        }
        Component header = paused
                ? Component.translatable("hud.session-stats.paused")
                : Component.translatable("hud.session-stats.recording");
        String timer = Format.timer(session.activeMillis);

        Bounds bounds = bounds(font, graphics.guiWidth(), graphics.guiHeight(), config, header, timer, lines);
        int x = bounds.x();
        int y = bounds.y();
        Ui.panel(graphics, x, y, bounds.width(), bounds.height());

        // Header: dot, REC or PAUSED, timer on the right
        int dotColor = paused ? Ui.PAUSED : Ui.RECORDING;
        int textY = y + PADDING;
        graphics.fill(x + PADDING, textY + 1, x + PADDING + DOT, textY + 1 + DOT, dotColor);
        graphics.text(font, header, x + PADDING + DOT + 4, textY, dotColor, false);
        graphics.text(font, timer, x + bounds.width() - PADDING - font.width(timer), textY, Ui.TEXT, false);

        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            int lineY = textY + LINE_HEIGHT * (i + 1) + 2;
            graphics.text(font, line.label(), x + PADDING, lineY, Ui.MUTED, false);
            graphics.text(font, line.value(), x + bounds.width() - PADDING - font.width(line.value()), lineY, Ui.ACCENT, false);
        }
        return bounds;
    }

    private static Bounds bounds(Font font, int screenWidth, int screenHeight, ModConfig config,
                                 Component header, String timer, List<Line> lines) {
        int width = DOT + 4 + font.width(header) + GAP + font.width(timer);
        for (Line line : lines) {
            width = Math.max(width, font.width(line.label()) + GAP + font.width(line.value()));
        }
        width += PADDING * 2;
        int height = PADDING * 2 + LINE_HEIGHT * (lines.size() + 1) - 2 + (lines.isEmpty() ? 0 : 2);

        ModConfig.Corner corner = config.hudCorner;
        int x = corner.right() ? screenWidth - width - config.hudOffsetX : config.hudOffsetX;
        int y = corner.bottom() ? screenHeight - height - config.hudOffsetY : config.hudOffsetY;
        x = Math.clamp(x, 0, Math.max(0, screenWidth - width));
        y = Math.clamp(y, 0, Math.max(0, screenHeight - height));
        return new Bounds(x, y, width, height);
    }
}
