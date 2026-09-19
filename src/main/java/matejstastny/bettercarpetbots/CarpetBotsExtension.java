package matejstastny.bettercarpetbots;

import carpet.CarpetExtension;
import com.mojang.brigadier.CommandDispatcher;
import matejstastny.bettercarpetbots.command.BotCommand;
import matejstastny.bettercarpetbots.command.BotsCommand;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

public class CarpetBotsExtension implements CarpetExtension {

    @Override
    public void onServerLoaded(MinecraftServer server) {
        BotConfig.load();
        BotManager.ensureBotTeam(server);
        BotManager.loadRealPlayers(server);
        if (BotConfig.get().skinUrl != null) {
            BotManager.setGlobalBotSkinUrl(BotConfig.get().skinUrl);
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        BotCommand.register(dispatcher);
        BotsCommand.register(dispatcher);
    }

    @Override
    public String version() {
        return "1.3.1";
    }

    @Override
    public void onPlayerLoggedIn(net.minecraft.server.level.ServerPlayer player) {
        BotManager.onPlayerJoin(player);
    }
}
