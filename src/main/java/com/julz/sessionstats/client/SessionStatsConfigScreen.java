package com.julz.sessionstats.client;

import com.julz.sessionstats.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

public class SessionStatsConfigScreen extends OptionsSubScreen {
    private static final String PREFIX = "session-stats.config.";

    private final OptionInstance<Boolean> autoStart;
    private final OptionInstance<Boolean> showHud;
    private final OptionInstance<Boolean> showSummaryOnStop;
    private final OptionInstance<Boolean> showMessages;
    private final OptionInstance<Integer> refreshSeconds;

    public SessionStatsConfigScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(PREFIX + "title"));
        ModConfig config = ModConfig.getInstance();
        this.autoStart = booleanOption("autoStart", config.autoStart);
        this.showHud = booleanOption("showHud", config.showHud);
        this.showSummaryOnStop = booleanOption("showSummaryOnStop", config.showSummaryOnStop);
        this.showMessages = booleanOption("showMessages", config.showMessages);
        this.refreshSeconds = new OptionInstance<>(
                PREFIX + "refreshSeconds",
                OptionInstance.cachedConstantTooltip(Component.translatable(PREFIX + "refreshSeconds.tooltip")),
                (caption, v) -> Options.genericValueLabel(caption, Component.translatable("session-stats.unit.seconds", v)),
                new OptionInstance.IntRange(10, 300),
                Math.clamp(config.refreshSeconds, 10, 300),
                v -> {
                });
    }

    @Override
    protected void addOptions() {
        this.list.addBig(this.autoStart);
        this.list.addSmall(this.showHud, this.showSummaryOnStop);
        this.list.addBig(this.showMessages);
        this.list.addSmall(
                Button.builder(Component.translatable(PREFIX + "editHud"), b -> Ui.open(new HudEditorScreen(this))).build(),
                Button.builder(Component.translatable(PREFIX + "history"), b -> Ui.open(new HistoryScreen(this))).build());
        this.list.addHeader(Component.translatable(PREFIX + "advanced"));
        this.list.addBig(this.refreshSeconds);
    }

    @Override
    public void removed() {
        super.removed();
        ModConfig config = ModConfig.getInstance();
        config.autoStart = this.autoStart.get();
        config.showHud = this.showHud.get();
        config.showSummaryOnStop = this.showSummaryOnStop.get();
        config.showMessages = this.showMessages.get();
        config.refreshSeconds = this.refreshSeconds.get();
        config.sanitize();
        ModConfig.save();
    }

    private static OptionInstance<Boolean> booleanOption(String name, boolean value) {
        return OptionInstance.createBoolean(PREFIX + name,
                OptionInstance.cachedConstantTooltip(Component.translatable(PREFIX + name + ".tooltip")),
                value);
    }
}
