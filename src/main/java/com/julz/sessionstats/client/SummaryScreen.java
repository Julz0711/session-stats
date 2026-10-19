package com.julz.sessionstats.client;

import com.julz.sessionstats.core.Format;
import com.julz.sessionstats.core.Session;
import com.julz.sessionstats.core.SessionHistory;
import com.julz.sessionstats.core.SessionTotals;
import com.julz.sessionstats.core.StatKeys;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Opens when a session stops: big totals with per-hour rates, the top blocks mined and the ores. */
public class SummaryScreen extends Screen {
    private static final int COLUMNS = 4;
    private static final int GAP = 4;
    private static final int CARD_HEIGHT = 38;
    private static final int ROW_HEIGHT = 17;
    private static final int MAX_ROWS = 5;

    private final Screen parent;
    private final Session session;
    private final SessionTotals totals;

    public SummaryScreen(Screen parent, Session session) {
        super(Component.translatable("session-stats.summary.title"));
        this.parent = parent;
        this.session = session;
        this.totals = session.totals();
    }

    private record Card(Component label, String value, String sub) {
    }

    @Override
    protected void init() {
        int buttonWidth = 100;
        int y = this.height - 26;
        int left = this.width / 2 - buttonWidth * 3 / 2 - GAP;
        addRenderableWidget(Button.builder(Component.translatable("session-stats.summary.copy"), button -> copy())
                .bounds(left, y, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("session-stats.summary.history"),
                        button -> Ui.open(new HistoryScreen(this)))
                .bounds(left + buttonWidth + GAP, y, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + (buttonWidth + GAP) * 2, y, buttonWidth, 20).build());
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Header
        graphics.centeredText(this.font, this.title, this.width / 2, 10, Ui.ACCENT);
        graphics.centeredText(this.font, subtitle(), this.width / 2, 22, Ui.MUTED);

        // Totals: two rows of cards
        List<Card> cards = cards();
        int cardWidth = Math.min(120, (this.width - 24 - GAP * (COLUMNS - 1)) / COLUMNS);
        int gridWidth = cardWidth * COLUMNS + GAP * (COLUMNS - 1);
        int gridX = (this.width - gridWidth) / 2;
        int gridY = 36;
        for (int i = 0; i < cards.size(); i++) {
            int x = gridX + (i % COLUMNS) * (cardWidth + GAP);
            int y = gridY + (i / COLUMNS) * (CARD_HEIGHT + GAP);
            card(graphics, cards.get(i), x, y, cardWidth);
        }

        // Top blocks and ores side by side, as many rows as fit above the buttons
        int listsY = gridY + 2 * (CARD_HEIGHT + GAP) + 2;
        int rows = Math.clamp((this.height - 32 - listsY - 18) / ROW_HEIGHT, 0, MAX_ROWS);
        if (rows > 0) {
            int listWidth = (gridWidth - GAP) / 2;
            List<Map.Entry<String, Long>> ores = new ArrayList<>();
            for (Map.Entry<String, Long> ore : totals.ores().entrySet()) {
                ores.add(Map.entry(StatKeys.oreBlock(ore.getKey()), ore.getValue()));
            }
            blockList(graphics, Component.translatable("session-stats.summary.top_mined"), totals.topMined(),
                    gridX, listsY, listWidth, rows);
            blockList(graphics, Component.translatable("session-stats.summary.ores"), ores,
                    gridX + listWidth + GAP, listsY, listWidth, rows);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private Component subtitle() {
        String date = SessionHistory.DATE.format(Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()));
        return Component.translatable("session-stats.summary.subtitle", session.world, date);
    }

    private List<Card> cards() {
        return List.of(
                statCard(SessionTotals.Stat.BLOCKS_MINED),
                statCard(SessionTotals.Stat.ORES_MINED),
                statCard(SessionTotals.Stat.MOB_KILLS),
                statCard(SessionTotals.Stat.DISTANCE),
                new Card(Component.translatable("session-stats.stat.active_time"), Format.duration(session.activeMillis),
                        Component.translatable(session.finished ? "session-stats.summary.finished" : "session-stats.summary.unfinished").getString()),
                statCard(SessionTotals.Stat.BLOCKS_PLACED),
                statCard(SessionTotals.Stat.XP),
                statCard(SessionTotals.Stat.DEATHS));
    }

    private Card statCard(SessionTotals.Stat stat) {
        String rate = Format.statRate(stat, totals.perHour(totals.get(stat)));
        return new Card(Ui.statName(stat), Ui.statValue(totals, stat),
                Component.translatable("session-stats.summary.per_hour", rate).getString());
    }

    private void card(GuiGraphicsExtractor graphics, Card card, int x, int y, int width) {
        Ui.panel(graphics, x, y, width, CARD_HEIGHT);
        graphics.fill(x, y, x + 2, y + CARD_HEIGHT, Ui.ACCENT);
        graphics.text(this.font, Ui.fit(this.font, card.label().getString(), width - 10), x + 6, y + 5, Ui.MUTED, false);
        String value = card.value();
        float scale = this.font.width(value) * 2 <= width - 10 ? 2f : 1.5f;
        Ui.scaledText(graphics, this.font, value, x + 6, y + 15, Ui.TEXT, scale);
        graphics.text(this.font, Ui.fit(this.font, card.sub(), width - 10), x + 6, y + CARD_HEIGHT - 10, Ui.ACCENT, false);
    }

    private void blockList(GuiGraphicsExtractor graphics, Component title, List<Map.Entry<String, Long>> blocks,
                           int x, int y, int width, int rows) {
        int height = 16 + rows * ROW_HEIGHT;
        Ui.panel(graphics, x, y, width, height);
        graphics.text(this.font, title, x + 6, y + 5, Ui.MUTED, false);
        if (blocks.isEmpty()) {
            graphics.text(this.font, Component.translatable("session-stats.summary.none"), x + 6, y + 20, Ui.MUTED, false);
            return;
        }
        for (int i = 0; i < Math.min(rows, blocks.size()); i++) {
            Map.Entry<String, Long> entry = blocks.get(i);
            int rowY = y + 15 + i * ROW_HEIGHT;
            ItemStack icon = Ui.blockIcon(entry.getKey());
            if (!icon.isEmpty()) {
                graphics.item(icon, x + 5, rowY);
            }
            String count = Format.number(entry.getValue());
            int countWidth = this.font.width(count);
            String name = Ui.fit(this.font, Ui.blockName(entry.getKey()).getString(), width - 34 - countWidth);
            graphics.text(this.font, name, x + 25, rowY + 4, Ui.TEXT, false);
            graphics.text(this.font, count, x + width - 6 - countWidth, rowY + 4, Ui.ACCENT, false);
        }
    }

    /** The summary as plain text, for pasting into chat, Discord or a video description. */
    private void copy() {
        StringBuilder text = new StringBuilder();
        text.append(subtitle().getString()).append('\n');
        for (Card card : cards()) {
            text.append(card.label().getString()).append(": ").append(card.value())
                    .append(" (").append(card.sub()).append(")\n");
        }
        if (!totals.ores().isEmpty()) {
            text.append(Component.translatable("session-stats.summary.ores").getString()).append(": ");
            List<String> ores = new ArrayList<>();
            totals.ores().forEach((ore, count) ->
                    ores.add(Ui.blockName(StatKeys.oreBlock(ore)).getString() + " " + Format.number(count)));
            text.append(String.join(", ", ores)).append('\n');
        }
        this.minecraft.keyboardHandler.setClipboard(text.toString());
        Toasts.show(this.minecraft, Component.translatable("session-stats.toast.copied"), Component.empty());
    }
}
