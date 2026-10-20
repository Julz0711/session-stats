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

/** Opens when a session stops: the totals that happened with per-hour rates, the top blocks mined and the ores. */
public class SummaryScreen extends Screen {
    private static final int MAX_COLUMNS = 4;
    private static final int GAP = 6;
    private static final int CARD_WIDTH = 110;
    private static final int CARD_HEIGHT = 48;
    private static final int ROW_HEIGHT = 17;
    // The stats that get a card, when they are not zero. Distance has one card; walked, flown and ridden are HUD only.
    private static final List<SessionTotals.Stat> CARD_STATS = List.of(
            SessionTotals.Stat.BLOCKS_MINED, SessionTotals.Stat.BLOCKS_PLACED, SessionTotals.Stat.ORES_MINED,
            SessionTotals.Stat.DISTANCE, SessionTotals.Stat.MOB_KILLS, SessionTotals.Stat.XP, SessionTotals.Stat.DEATHS);

    private final Screen parent;
    private final Session session;
    private final SessionTotals totals;

    public SummaryScreen(Screen parent, Session session) {
        super(Component.translatable("session-stats.summary.title"));
        this.parent = parent;
        this.session = session;
        this.totals = session.totals();
    }

    /** A big number with its label above and its rate per hour below (empty when too short to say). */
    private record Card(Component label, String value, String rate) {
    }

    @Override
    protected void init() {
        int buttonWidth = 100;
        int y = this.height - 26;
        int left = this.width / 2 - buttonWidth * 3 / 2 - 4;
        addRenderableWidget(Button.builder(Component.translatable("session-stats.summary.copy"), button -> copy())
                .bounds(left, y, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("session-stats.summary.history"),
                        button -> Ui.open(new HistoryScreen(this)))
                .bounds(left + buttonWidth + 4, y, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + (buttonWidth + 4) * 2, y, buttonWidth, 20).build());
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Header: title, then world, date and how long the session was
        graphics.centeredText(this.font, this.title, this.width / 2, 12, Ui.ACCENT);
        graphics.centeredText(this.font, subtitle(), this.width / 2, 25, Ui.MUTED);

        List<Card> cards = cards();
        int bottom = this.height - 34;
        int y = 42;
        if (cards.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("session-stats.summary.empty"),
                    this.width / 2, (y + bottom) / 2 - 4, Ui.MUTED);
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            return;
        }

        // Only the stats that happened, in rows of up to 4 cards
        int columns = Math.min(MAX_COLUMNS, cards.size());
        int cardWidth = Math.min(CARD_WIDTH, (this.width - 24 - GAP * (columns - 1)) / columns);
        for (int i = 0; i < cards.size(); i++) {
            int row = i / columns;
            int inRow = Math.min(columns, cards.size() - row * columns);
            // A shorter last row is centered under the full ones
            int rowX = (this.width - (cardWidth * inRow + GAP * (inRow - 1))) / 2;
            card(graphics, cards.get(i), rowX + (i % columns) * (cardWidth + GAP), y + row * (CARD_HEIGHT + GAP), cardWidth);
        }
        y += ((cards.size() + columns - 1) / columns) * (CARD_HEIGHT + GAP) + 4;

        // The top blocks and the ores, only when there are any, side by side
        List<Map.Entry<String, Long>> ores = new ArrayList<>();
        for (Map.Entry<String, Long> ore : totals.ores().entrySet()) {
            ores.add(Map.entry(StatKeys.oreBlock(ore.getKey()), ore.getValue()));
        }
        List<Component> titles = new ArrayList<>();
        List<List<Map.Entry<String, Long>>> lists = new ArrayList<>();
        if (!totals.topMined().isEmpty()) {
            titles.add(Component.translatable("session-stats.summary.top_mined"));
            lists.add(totals.topMined());
        }
        if (!ores.isEmpty()) {
            titles.add(Component.translatable("session-stats.summary.ores"));
            lists.add(ores);
        }
        int maxRows = (bottom - y - 18) / ROW_HEIGHT;
        if (!lists.isEmpty() && maxRows > 0) {
            int listsWidth = Math.min(MAX_COLUMNS * CARD_WIDTH + (MAX_COLUMNS - 1) * GAP, this.width - 24);
            int listWidth = lists.size() == 1 ? Math.min(listsWidth, 2 * CARD_WIDTH + GAP) : (listsWidth - GAP) / 2;
            int listX = (this.width - (listWidth * lists.size() + GAP * (lists.size() - 1))) / 2;
            for (int i = 0; i < lists.size(); i++) {
                blockList(graphics, titles.get(i), lists.get(i), listX + i * (listWidth + GAP), y, listWidth,
                        Math.min(maxRows, SessionTotals.TOP_COUNT));
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private Component subtitle() {
        String date = SessionHistory.DATE.format(Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()));
        Component subtitle = Component.translatable("session-stats.summary.subtitle", session.world, date,
                Format.duration(session.activeMillis));
        if (!session.finished) {
            return Component.translatable("session-stats.summary.subtitle_autosaved", subtitle);
        }
        return subtitle;
    }

    private List<Card> cards() {
        List<Card> cards = new ArrayList<>();
        for (SessionTotals.Stat stat : CARD_STATS) {
            long value = totals.get(stat);
            if (value == 0) {
                continue;
            }
            double perHour = totals.perHour(value);
            String rate = perHour < 0 ? ""
                    : Component.translatable("session-stats.summary.per_hour", Format.statRate(stat, perHour)).getString();
            cards.add(new Card(Ui.statName(stat), Ui.statValue(totals, stat), rate));
        }
        return cards;
    }

    private void card(GuiGraphicsExtractor graphics, Card card, int x, int y, int width) {
        Ui.panel(graphics, x, y, width, CARD_HEIGHT);
        graphics.text(this.font, Ui.fit(this.font, card.label().getString(), width - 12), x + 6, y + 6, Ui.MUTED, false);
        String value = card.value();
        float scale = this.font.width(value) * 2 <= width - 12 ? 2f : 1.5f;
        Ui.scaledText(graphics, this.font, value, x + 6, y + 17, Ui.TEXT, scale);
        if (!card.rate().isEmpty()) {
            graphics.text(this.font, Ui.fit(this.font, card.rate(), width - 12), x + 6, y + CARD_HEIGHT - 11, Ui.ACCENT, false);
        }
    }

    private void blockList(GuiGraphicsExtractor graphics, Component title, List<Map.Entry<String, Long>> blocks,
                           int x, int y, int width, int maxRows) {
        int rows = Math.min(maxRows, blocks.size());
        int height = 20 + rows * ROW_HEIGHT;
        Ui.panel(graphics, x, y, width, height);
        graphics.text(this.font, title, x + 6, y + 6, Ui.MUTED, false);
        for (int i = 0; i < rows; i++) {
            Map.Entry<String, Long> entry = blocks.get(i);
            int rowY = y + 17 + i * ROW_HEIGHT;
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
            text.append(card.label().getString()).append(": ").append(card.value());
            if (!card.rate().isEmpty()) {
                text.append(" (").append(card.rate()).append(')');
            }
            text.append('\n');
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
