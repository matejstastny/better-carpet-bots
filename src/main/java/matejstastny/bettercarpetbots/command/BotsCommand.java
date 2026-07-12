package matejstastny.bettercarpetbots.command;

import carpet.fakes.ServerPlayerInterface;
import carpet.patches.EntityPlayerMPFake;
import matejstastny.bettercarpetbots.BotManager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

import static net.minecraft.server.command.CommandManager.*;

public class BotsCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("bots")
            .requires(requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))

            .then(literal("list")
                .executes(BotsCommand::listBots))

            .then(literal("stop")
                .executes(BotsCommand::stopAll))

            .then(literal("kill")
                .executes(BotsCommand::killAll))

            .then(literal("permissionLevel")
                .then(argument("level", IntegerArgumentType.integer(0, 4))
                    .executes(ctx -> {
                        int level = IntegerArgumentType.getInteger(ctx, "level");
                        BotManager.setBotPermissionLevel(level);
                        ctx.getSource().sendFeedback(() -> Text.literal("/bot permission level set to " + level + "."), true);
                        return level;
                    })))

            .then(literal("skin")
                .then(literal("all")
                    .then(argument("url", StringArgumentType.greedyString())
                        .executes(ctx -> skinAll(ctx, StringArgumentType.getString(ctx, "url")))))
                .then(argument("name", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (ServerPlayerEntity p : BotManager.getActiveBots(ctx.getSource().getServer())) {
                            builder.suggest(p.getNameForScoreboard());
                        }
                        return builder.buildFuture();
                    })
                    .then(argument("url", StringArgumentType.greedyString())
                        .executes(ctx -> skinOne(
                                ctx,
                                StringArgumentType.getString(ctx, "name"),
                                StringArgumentType.getString(ctx, "url"))))))
        );
    }

    private static int listBots(CommandContext<ServerCommandSource> ctx) {
        List<ServerPlayerEntity> bots = BotManager.getActiveBots(ctx.getSource().getServer());
        if (bots.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("No bots are currently online."), false);
        } else {
            StringBuilder sb = new StringBuilder("Online bots (" + bots.size() + "): ");
            for (int i = 0; i < bots.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(bots.get(i).getNameForScoreboard());
            }
            String msg = sb.toString();
            ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
        }
        return bots.size();
    }

    private static int stopAll(CommandContext<ServerCommandSource> ctx) {
        List<ServerPlayerEntity> bots = BotManager.getActiveBots(ctx.getSource().getServer());
        for (ServerPlayerEntity bot : bots) {
            ((ServerPlayerInterface) bot).getActionPack().stopAll();
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Stopped all actions for " + bots.size() + " bot(s)."), false);
        return bots.size();
    }

    private static int killAll(CommandContext<ServerCommandSource> ctx) {
        List<ServerPlayerEntity> bots = BotManager.getActiveBots(ctx.getSource().getServer());
        for (ServerPlayerEntity bot : bots) {
            if (bot instanceof EntityPlayerMPFake fake) {
                fake.kill(Text.literal("Removed by /bots kill"));
            } else {
                bot.networkHandler.disconnect(Text.literal("Removed by /bots kill"));
            }
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Killed " + bots.size() + " bot(s)."), false);
        return bots.size();
    }

    private static int skinOne(CommandContext<ServerCommandSource> ctx, String name, String url) {
        ServerPlayerEntity bot = ctx.getSource().getServer().getPlayerManager().getPlayer(name);
        if (bot == null || !(bot instanceof EntityPlayerMPFake)) {
            ctx.getSource().sendError(Text.literal("Bot '" + name + "' is not online."));
            return 0;
        }
        return applySkin(ctx, bot, url) ? 1 : 0;
    }

    private static int skinAll(CommandContext<ServerCommandSource> ctx, String url) {
        BotManager.setGlobalBotSkinUrl(url);
        List<ServerPlayerEntity> bots = BotManager.getActiveBots(ctx.getSource().getServer());
        int applied = 0;
        for (ServerPlayerEntity bot : bots) {
            if (BotManager.applySkin(ctx.getSource().getServer(), bot, url)) applied++;
        }
        int finalApplied = applied;
        ctx.getSource().sendFeedback(() -> Text.literal("Skin saved. Applied to " + finalApplied + "/" + bots.size() + " online bot(s)."), false);
        return applied;
    }

    private static boolean applySkin(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity bot, String url) {
        boolean ok = BotManager.applySkin(ctx.getSource().getServer(), bot, url);
        if (!ok) {
            String botName = bot.getNameForScoreboard();
            ctx.getSource().sendError(Text.literal("Skin command failed for '" + botName + "'."));
        }
        return ok;
    }
}
