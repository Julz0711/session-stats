package com.julz.sessionstats.client;

import com.julz.sessionstats.config.ModConfig;
import com.julz.sessionstats.core.Session;
import com.julz.sessionstats.core.SessionTotals;
import com.julz.sessionstats.core.StatKeys;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Drag the HUD to where it should sit, and pick which stats it shows. */
public class HudEditorScreen extends Screen {
    private final Screen parent;
    private final List<Button> statButtons = new ArrayList<>();
    private SessionHud.Bounds hudBounds;
    private boolean dragging = false;
    private double grabX;
    private double grabY;

    public HudEditorScreen(Screen parent) {
        super(Component.translatable("session-stats.hud_editor.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.statButtons.clear();
        SessionTotals.Stat[] stats = SessionTotals.Stat.values();
        int buttonWidth = 130;
        int columns = 2;
        int rows = (stats.length + columns - 1) / columns;
        int left = this.width / 2 - buttonWidth - 2;
        int top = this.height / 2 - rows * 12;
        for (int i = 0; i < stats.length; i++) {
            SessionTotals.Stat stat = stats[i];
            Button button = Button.builder(statLabel(stat), b -> toggle(stat))
                    .bounds(left + (i % columns) * (buttonWidth + 4), top + (i / columns) * 24, buttonWidth, 20)
                    .build();
            this.statButtons.add(button);
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 75, this.height - 28, 150, 20).build());
        updateButtons();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void removed() {
        super.removed();
        ModConfig.getInstance().sanitize();
        ModConfig.save();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, this.title, this.width / 2, 10, Ui.ACCENT);
        graphics.centeredText(this.font, Component.translatable("session-stats.hud_editor.hint",
                        ModConfig.MAX_HUD_STATS), this.width / 2, 22, Ui.MUTED);
        this.hudBounds = SessionHud.draw(graphics, this.font, previewSession(), false, ModConfig.getInstance());
        if (this.dragging || this.hudBounds.contains(mouseX, mouseY)) {
            graphics.outline(this.hudBounds.x() - 1, this.hudBounds.y() - 1,
                    this.hudBounds.width() + 2, this.hudBounds.height() + 2, Ui.ACCENT);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (this.hudBounds != null && this.hudBounds.contains(event.x(), event.y())) {
            this.dragging = true;
            this.grabX = event.x() - this.hudBounds.x();
            this.grabY = event.y() - this.hudBounds.y();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging && this.hudBounds != null) {
            moveHud((int) Math.round(event.x() - this.grabX), (int) Math.round(event.y() - this.grabY));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragging) {
            this.dragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    /** Attaches the HUD to the corner nearest to it, so it stays in place when the window is resized. */
    private void moveHud(int x, int y) {
        int width = this.hudBounds.width();
        int height = this.hudBounds.height();
        x = Math.clamp(x, 0, Math.max(0, this.width - width));
        y = Math.clamp(y, 0, Math.max(0, this.height - height));
        boolean right = x + width / 2 > this.width / 2;
        boolean bottom = y + height / 2 > this.height / 2;
        ModConfig config = ModConfig.getInstance();
        config.hudCorner = ModConfig.Corner.of(right, bottom);
        config.hudOffsetX = right ? this.width - x - width : x;
        config.hudOffsetY = bottom ? this.height - y - height : y;
    }

    private void toggle(SessionTotals.Stat stat) {
        ModConfig config = ModConfig.getInstance();
        if (!config.hudStats.remove(stat.name()) && config.hudStats.size() < ModConfig.MAX_HUD_STATS) {
            config.hudStats.add(stat.name());
        }
        updateButtons();
    }

    private void updateButtons() {
        ModConfig config = ModConfig.getInstance();
        SessionTotals.Stat[] stats = SessionTotals.Stat.values();
        for (int i = 0; i < stats.length; i++) {
            Button button = this.statButtons.get(i);
            boolean shown = config.hudStats.contains(stats[i].name());
            button.setMessage(statLabel(stats[i]));
            button.active = shown || config.hudStats.size() < ModConfig.MAX_HUD_STATS;
        }
    }

    private static Component statLabel(SessionTotals.Stat stat) {
        boolean shown = ModConfig.getInstance().hudStats.contains(stat.name());
        return Component.translatable(shown ? "session-stats.hud_editor.shown" : "session-stats.hud_editor.hidden",
                Ui.statName(stat));
    }

    /** The running session, or example numbers when nothing is recording. */
    private static Session previewSession() {
        Session current = SessionController.recorder().current();
        if (current != null) {
            return current;
        }
        Session sample = new Session();
        sample.activeMillis = 47 * 60_000 + 12_000;
        sample.xp = 1_240;
        sample.stats.put(StatKeys.of(StatKeys.MINED, "minecraft:stone"), 1_874L);
        sample.stats.put(StatKeys.of(StatKeys.MINED, "minecraft:diamond_ore"), 23L);
        sample.stats.put(StatKeys.of(StatKeys.PLACED, "minecraft:cobblestone"), 312L);
        sample.stats.put(StatKeys.MOB_KILLS, 41L);
        sample.stats.put(StatKeys.WALK, 342_000L);
        return sample;
    }
}
