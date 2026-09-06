package matejstastny.bettercarpetbots;

import carpet.CarpetServer;
import carpet.patches.EntityPlayerMPFake;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

public final class BotManager {
    public static final String TEAM_NAME = "bots";

    private static int botPermissionLevel = 0;
    private static String globalBotSkinUrl = null;
    private static final Set<String> realPlayerNames = new HashSet<>();

    private static final PermissionCheck[] LEVEL_CHECKS = {
        Commands.LEVEL_ALL,
        Commands.LEVEL_MODERATORS,
        Commands.LEVEL_GAMEMASTERS,
        Commands.LEVEL_ADMINS,
        Commands.LEVEL_OWNERS,
    };

    public static int getBotPermissionLevel() {
        return botPermissionLevel;
    }

    public static void setBotPermissionLevel(int level) {
        botPermissionLevel = level;
    }

    public static PermissionCheck getBotPermissionCheck() {
        return LEVEL_CHECKS[Math.max(0, Math.min(4, botPermissionLevel))];
    }

    private BotManager() {}

    public static void ensureBotTeam(MinecraftServer server) {
        Scoreboard sb = server.getScoreboard();
        PlayerTeam team = sb.getPlayerTeam(TEAM_NAME);
        if (team == null) {
            team = sb.addPlayerTeam(TEAM_NAME);
        }
        team.setColor(ChatFormatting.GREEN);
        team.setPlayerPrefix(Component.literal("[Bot] ").withStyle(ChatFormatting.GREEN));
    }

    public static void addToTeam(MinecraftServer server, String playerName) {
        ensureBotTeam(server);
        Scoreboard sb = server.getScoreboard();
        PlayerTeam team = sb.getPlayerTeam(TEAM_NAME);
        sb.addPlayerToTeam(playerName, team);
    }

    public static String getGlobalBotSkinUrl() {
        return globalBotSkinUrl;
    }

    public static void setGlobalBotSkinUrl(String url) {
        if (url.startsWith("\"") && url.endsWith("\"")) {
            url = url.substring(1, url.length() - 1);
        }
        globalBotSkinUrl = url;
    }

    public static void applyGlobalSkinToAllBots(MinecraftServer server) {
        if (globalBotSkinUrl == null) return;
        for (ServerPlayer bot : getActiveBots(server)) {
            applySkin(server, bot, globalBotSkinUrl);
        }
    }

    public static boolean applySkin(MinecraftServer server, ServerPlayer bot, String url) {
        try {
            server.getCommands()
                    .performPrefixedCommand(
                            bot.createCommandSourceStack()
                                    .withPermission(LevelBasedPermissionSet.OWNER)
                                    .withSuppressedOutput(),
                            "skin set web slim \"" + url + "\"");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Called from CarpetExtension.onPlayerLoggedIn for every joining player. */
    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer server = CarpetServer.minecraft_server;
        if (player instanceof EntityPlayerMPFake) {
            addToTeam(server, player.getScoreboardName());
            if (globalBotSkinUrl != null) {
                applySkin(server, player, globalBotSkinUrl);
            }
        } else {
            markAsRealPlayer(server, player.getScoreboardName());
        }
    }

    // --- Real player protection ---

    public static boolean isRealPlayer(String name) {
        return realPlayerNames.contains(name);
    }

    /**
     * Records a name as belonging to a real (non-fake) player. On the first call for a given
     * name, deletes any offline-UUID player data that may have been written by a bot with that
     * name, then persists the updated list to disk.
     */
    public static void markAsRealPlayer(MinecraftServer server, String name) {
        if (realPlayerNames.add(name)) {
            deleteOfflinePlayerData(server, name);
            saveRealPlayers(server);
        }
    }

    public static void loadRealPlayers(MinecraftServer server) {
        realPlayerNames.clear();
        Path file = getRealPlayersFile(server);
        if (!Files.exists(file)) return;
        try {
            Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(realPlayerNames::add);
        } catch (IOException e) {
            // leave set empty on read error
        }
    }

    private static void saveRealPlayers(MinecraftServer server) {
        try {
            Path file = getRealPlayersFile(server);
            Files.createDirectories(file.getParent());
            Files.write(file, realPlayerNames, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // data remains in memory for this session
        }
    }

    private static void deleteOfflinePlayerData(MinecraftServer server, String name) {
        try {
            UUID offlineUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            Path playerDataDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
            Path backupDir = server.getWorldPath(LevelResource.ROOT).resolve("bot-backup");
            Files.createDirectories(backupDir);

            Path dat = playerDataDir.resolve(offlineUUID + ".dat");
            if (Files.exists(dat)) {
                Files.copy(dat, backupDir.resolve(name + ".bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.delete(dat);
            }
            Files.deleteIfExists(playerDataDir.resolve(offlineUUID + ".dat_old"));
        } catch (Exception e) {
            // ignore - best-effort cleanup
        }
    }

    private static Path getRealPlayersFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("better-carpet-bots")
                .resolve("real-players.txt");
    }

    // ---

    public static boolean isBotMember(MinecraftServer server, String name) {
        PlayerTeam team = server.getScoreboard().getPlayerTeam(TEAM_NAME);
        return team != null && team.getPlayers().contains(name);
    }

    public static List<ServerPlayer> getActiveBots(MinecraftServer server) {
        List<ServerPlayer> bots = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof EntityPlayerMPFake) {
                bots.add(player);
            }
        }
        return bots;
    }

    /** Returns the next available auto-name: Bot1, Bot2, ... */
    public static String nextAutoName(MinecraftServer server) {
        int i = 1;
        while (true) {
            String name = "Bot" + i;
            if (server.getPlayerList().getPlayer(name) == null) {
                return name;
            }
            i++;
        }
    }
}
