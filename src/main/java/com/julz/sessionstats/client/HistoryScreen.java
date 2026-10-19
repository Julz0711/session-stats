package com.julz.sessionstats.client;

import com.julz.sessionstats.core.Format;
import com.julz.sessionstats.core.Session;
import com.julz.sessionstats.core.SessionHistory;
import com.julz.sessionstats.core.SessionTotals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Past sessions of this world or server, or of all of them, sortable by date or any stat. */
public class HistoryScreen extends Screen {
    private static final DateTimeFormatter EXPORT_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);

    private final Screen parent;
    private SessionHistory.Sort sort = SessionHistory.Sort.DATE;
    // null shows every world
    private String folder;
    private List<Session> sessions = List.of();
    private SessionList list;
    private Button openButton;

    public HistoryScreen(Screen parent) {
        super(Component.translatable("session-stats.history.title"));
        this.parent = parent;
        // Inside a world: that world's sessions. On the title screen: all of them.
        this.folder = Minecraft.getInstance().level != null ? SessionController.worldFolder() : null;
    }

    @Override
    protected void init() {
        loadSessions();
        int top = 32;
        int bottom = this.height - 32;
        this.list = addRenderableWidget(new SessionList(this.minecraft, this.width, bottom - top, top));
        this.list.setSessions(SessionHistory.sorted(this.sessions, this.sort));

        int buttonWidth = 120;
        addRenderableWidget(Button.builder(sortLabel(), button -> {
            this.sort = this.sort.next();
            button.setMessage(sortLabel());
            this.list.setSessions(SessionHistory.sorted(this.sessions, this.sort));
        }).bounds(this.width / 2 - buttonWidth - 2, 6, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(scopeLabel(), button -> {
            this.folder = this.folder == null && this.minecraft.level != null ? SessionController.worldFolder() : null;
            rebuildWidgets();
        }).bounds(this.width / 2 + 2, 6, buttonWidth, 20).build());

        int y = this.height - 26;
        int width = 100;
        int left = this.width / 2 - width * 3 / 2 - 4;
        this.openButton = addRenderableWidget(Button.builder(Component.translatable("session-stats.history.open"),
                button -> openSelected()).bounds(left, y, width, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("session-stats.history.export"),
                button -> export()).bounds(left + width + 4, y, width, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + (width + 4) * 2, y, width, 20).build());
        updateButtons();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (this.sessions.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("session-stats.history.empty"),
                    this.width / 2, this.height / 2 - 4, Ui.MUTED);
        }
    }

    private void loadSessions() {
        try {
            this.sessions = SessionController.store().load(this.folder);
        } catch (IOException e) {
            SessionStatsClient.LOGGER.error("Failed to read session history", e);
            this.sessions = List.of();
        }
    }

    private Component sortLabel() {
        return Component.translatable("session-stats.history.sort",
                Component.translatable("session-stats.sort." + this.sort.name().toLowerCase(Locale.ROOT)));
    }

    private Component scopeLabel() {
        return Component.translatable(this.folder != null ? "session-stats.history.this_world" : "session-stats.history.all_worlds");
    }

    private void updateButtons() {
        if (this.openButton != null) {
            this.openButton.active = this.list != null && this.list.getSelected() != null;
        }
    }

    private void openSelected() {
        SessionList.Entry entry = this.list.getSelected();
        if (entry != null) {
            Ui.open(new SummaryScreen(this, entry.session));
        }
    }

    /** Writes the sessions shown to a CSV file next to the session folders. */
    private void export() {
        List<Session> shown = SessionHistory.sorted(this.sessions, this.sort);
        Path file = SessionController.store().root().resolve("export-" + EXPORT_NAME.format(LocalDateTime.now()) + ".csv");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, SessionHistory.toCsv(shown, ZoneId.systemDefault()));
            this.minecraft.keyboardHandler.setClipboard(file.toAbsolutePath().toString());
            Toasts.show(this.minecraft, Component.translatable("session-stats.toast.exported", shown.size()),
                    Component.literal(file.getFileName().toString()));
        } catch (IOException e) {
            SessionStatsClient.LOGGER.error("Failed to export {}", file, e);
            Toasts.show(this.minecraft, Component.translatable("session-stats.toast.export_failed"),
                    Component.literal(e.getMessage() != null ? e.getMessage() : ""));
        }
    }

    final class SessionList extends ObjectSelectionList<SessionList.Entry> {
        SessionList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 26);
        }

        void setSessions(List<Session> sessions) {
            List<Entry> entries = new ArrayList<>();
            for (Session session : sessions) {
                entries.add(new Entry(session));
            }
            replaceEntries(entries);
            setScrollAmount(0);
            updateButtons();
        }

        @Override
        public int getRowWidth() {
            return Math.min(360, this.width - 20);
        }

        @Override
        public void setSelected(Entry entry) {
            super.setSelected(entry);
            updateButtons();
        }

        final class Entry extends ObjectSelectionList.Entry<Entry> {
            final Session session;
            private final SessionTotals totals;
            private long lastClickMillis = 0;

            Entry(Session session) {
                this.session = session;
                this.totals = session.totals();
            }

            @Override
            public Component getNarration() {
                return Component.literal(title());
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
                int x = getContentX();
                int y = getContentY();
                int width = getContentWidth();
                var font = HistoryScreen.this.font;

                String value = sortValue();
                int valueWidth = font.width(value);
                graphics.text(font, Ui.fit(font, title(), width - valueWidth - 8), x + 2, y + 2, Ui.TEXT, false);
                graphics.text(font, value, x + width - valueWidth - 2, y + 2, Ui.ACCENT, false);

                String details = String.join("  ·  ",
                        Format.duration(this.session.activeMillis),
                        Format.number(this.totals.blocksMined()) + " " + Ui.statName(SessionTotals.Stat.BLOCKS_MINED).getString(),
                        Format.number(this.totals.oresMined()) + " " + Ui.statName(SessionTotals.Stat.ORES_MINED).getString(),
                        Format.distance(this.totals.distanceCm()));
                graphics.text(font, Ui.fit(font, details, width - 4), x + 2, y + 13, Ui.MUTED, false);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                SessionList.this.setSelected(this);
                long now = System.currentTimeMillis();
                if (doubleClick || now - this.lastClickMillis < 250) {
                    openSelected();
                }
                this.lastClickMillis = now;
                return true;
            }

            private String title() {
                String date = SessionHistory.DATE.format(Instant.ofEpochMilli(this.session.startedAt).atZone(ZoneId.systemDefault()));
                String title = HistoryScreen.this.folder == null ? date + "  " + this.session.world : date;
                return this.session.finished ? title : title + " *";
            }

            private String sortValue() {
                return switch (HistoryScreen.this.sort) {
                    case DATE, LENGTH -> Format.duration(this.session.activeMillis);
                    default -> Ui.statValue(this.totals, HistoryScreen.this.sort.stat);
                };
            }
        }
    }
}
