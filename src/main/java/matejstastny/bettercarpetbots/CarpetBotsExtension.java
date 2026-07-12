package matejstastny.bettercarpetbots;

import carpet.CarpetExtension;
import com.mojang.brigadier.CommandDispatcher;
import matejstastny.bettercarpetbots.command.BotCommand;
import matejstastny.bettercarpetbots.command.BotsCommand;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

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
    public void registerCommands(
            CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        BotCommand.register(dispatcher);
        BotsCommand.register(dispatcher);
    }

    @Override
    public String version() {
        return "1.0.1";
    }

    @Override
    public void onPlayerLoggedIn(net.minecraft.server.network.ServerPlayerEntity player) {
        BotManager.onPlayerJoin(player);
    }
}
