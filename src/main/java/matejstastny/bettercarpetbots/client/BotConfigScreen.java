package matejstastny.bettercarpetbots.client;

import matejstastny.bettercarpetbots.BotConfig;
import matejstastny.bettercarpetbots.BotManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

public class BotConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget skinUrlField;

    public BotConfigScreen(Screen parent) {
        super(Text.literal("Better Carpet Bots"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        this.skinUrlField = new TextFieldWidget(this.textRenderer, cx - 150, cy - 10, 300, 20, Text.empty());
        this.skinUrlField.setMaxLength(512);
        String current = BotConfig.get().skinUrl;
        this.skinUrlField.setText(current != null ? current : "");
        this.addDrawableChild(this.skinUrlField);
        this.setFocused(this.skinUrlField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save & Apply"), btn -> save())
                .dimensions(cx - 155, cy + 20, 150, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> this.client.setScreen(parent))
                .dimensions(cx + 5, cy + 20, 150, 20)
                .build());
    }

    private void save() {
        String url = this.skinUrlField.getText().trim();
        BotConfig.get().skinUrl = url.isEmpty() ? null : url;
        BotConfig.save();

        if (!url.isEmpty()) {
            MinecraftServer server = MinecraftClient.getInstance().getServer();
            if (server != null) {
                server.execute(() -> {
                    BotManager.setGlobalBotSkinUrl(url);
                    BotManager.applyGlobalSkinToAllBots(server);
                });
            }
        }

        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(
                this.textRenderer, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        context.drawTextWithShadow(
                this.textRenderer, Text.literal("Bot Skin URL"), this.width / 2 - 150, this.height / 2 - 25, 0xA0A0A0);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
