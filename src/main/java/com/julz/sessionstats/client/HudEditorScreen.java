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
import java.util.Locale;
import java.util.function.Supplier;

/** Drag the HUD to where it should sit, and pick which stats it shows. */
public class HudEditorScreen extends Screen {
    private static final int COLUMNS = 4;
    private static final int GAP = 4;
    private static final int ROW = 22;
    private static final int SECTION = 12;
    private static final int[] SCALES = {50, 75, 100, 125, 150, 200};
    private static final int[] OPACITIES = {0, 25, 50, 80, 100};

    private final Screen parent;
    private final List<Button> statButtons = new ArrayList<>();
    private int sectionX;
    private int statsY;
    private int styleY;
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
        int buttonWidth = Math.min(96, (this.width - 20 - GAP * (COLUMNS - 1)) / COLUMNS);
        int gridWidth = buttonWidth * COLUMNS + GAP * (COLUMNS - 1);
        int left = (this.width - gridWidth) / 2;
        int statRows = (stats.length + COLUMNS - 1) / COLUMNS;
        int styleRows = 2;
        int blockHeight = SECTION + statRows * ROW + SECTION + styleRows * ROW;
        this.statsY = Math.max(36, (this.height - blockHeight) / 2);
        int top = this.statsY + SECTION;
        for (int i = 0; i < stats.length; i++) {
            SessionTotals.Stat stat = stats[i];
            Button button = Button.builder(statLabel(stat), b -> toggle(stat))
                    .bounds(left + (i % COLUMNS) * (buttonWidth + GAP), top + (i / COLUMNS) * ROW, buttonWidth, 20)
                    .build();
            this.statButtons.add(button);
            addRenderableWidget(button);
        }

        this.styleY = top + statRows * ROW;
        List<StyleOption> style = List.of(
                new StyleOption(() -> label("size", Component.literal(config().hudScale + "%")), () -> {
                    config().hudScale = nextOf(SCALES, config().hudScale);
                }),
                new StyleOption(() -> label("background", Component.literal(config().hudOpacity + "%")), () -> {
                    config().hudOpacity = nextOf(OPACITIES, config().hudOpacity);
                }),
                new StyleOption(() -> label("color", Component.translatable(
                        "session-stats.hud_editor.color." + config().hudColor.name().toLowerCase(Locale.ROOT))), () -> {
                    config().hudColor = config().hudColor.next();
                }),
                new StyleOption(() -> toggleLabel("timer", config().hudShowTimer), () -> {
                    config().hudShowTimer = !config().hudShowTimer;
                }),
                new StyleOption(() -> toggleLabel("rates", config().hudShowRates), () -> {
                    config().hudShowRates = !config().hudShowRates;
                }),
                new StyleOption(() -> toggleLabel("ores", config().hudShowOres), () -> {
                    config().hudShowOres = !config().hudShowOres;
                }),
                new StyleOption(() -> toggleLabel("shadow", config().hudTextShadow), () -> {
                    config().hudTextShadow = !config().hudTextShadow;
                }));
        for (int i = 0; i < style.size(); i++) {
            StyleOption option = style.get(i);
            addRenderableWidget(Button.builder(option.label().get(), b -> {
                        option.action().run();
                        b.setMessage(option.label().get());
                    })
                    .bounds(left + (i % COLUMNS) * (buttonWidth + GAP), this.styleY + SECTION + (i / COLUMNS) * ROW,
                            buttonWidth, 20)
                    .build());
        }
        this.sectionX = left;

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 75, this.height - 28, 150, 20).build());
        updateButtons();
    }

    /** A look option: clicking runs {@code action}, then the button shows what {@code label} computes. */
    private record StyleOption(Supplier<Component> label, Runnable action) {
    }

    private static Component label(String name, Component value) {
        return Component.translatable("session-stats.hud_editor.option",
                Component.translatable("session-stats.hud_editor." + name), value);
    }

    private static Component toggleLabel(String name, boolean on) {
        return label(name, Component.translatable(on ? "options.on" : "options.off"));
    }

    private static int nextOf(int[] values, int current) {
        for (int value : values) {
            if (value > current) {
                return value;
            }
        }
        return values[0];
    }

    private static ModConfig config() {
        return ModConfig.getInstance();
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
        graphics.centeredText(this.font, Component.translatable("session-stats.hud_editor.hint"),
                this.width / 2, 22, Ui.MUTED);
        graphics.text(this.font, Component.translatable("session-stats.hud_editor.stats"),
                this.sectionX, this.statsY + 2, Ui.MUTED, false);
        graphics.text(this.font, Component.translatable("session-stats.hud_editor.style"),
                this.sectionX, this.styleY + 2, Ui.MUTED, false);
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
        if (!config.hudStats.remove(stat.name())) {
            config.hudStats.add(stat.name());
        }
        updateButtons();
    }

    private void updateButtons() {
        SessionTotals.Stat[] stats = SessionTotals.Stat.values();
        for (int i = 0; i < stats.length; i++) {
            this.statButtons.get(i).setMessage(statLabel(stats[i]));
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
        sample.stats.put(StatKeys.of(StatKeys.MINED, "minecraft:deepslate_iron_ore"), 64L);
        sample.stats.put(StatKeys.of(StatKeys.PLACED, "minecraft:cobblestone"), 312L);
        sample.stats.put(StatKeys.MOB_KILLS, 41L);
        sample.stats.put(StatKeys.WALK, 342_000L);
        return sample;
    }
}
