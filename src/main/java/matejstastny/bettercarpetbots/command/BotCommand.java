package matejstastny.bettercarpetbots.command;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import carpet.helpers.EntityPlayerActionPack.Action;
import carpet.helpers.EntityPlayerActionPack.ActionType;
import carpet.patches.EntityPlayerMPFake;
import matejstastny.bettercarpetbots.BotManager;
import matejstastny.bettercarpetbots.screen.BotInventoryHandler;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import static net.minecraft.server.command.CommandManager.*;

public class BotCommand {

    private static final SimpleCommandExceptionType BOT_NOT_FOUND =
            new SimpleCommandExceptionType(Text.literal("Bot not found or not online."));
    private static final SimpleCommandExceptionType NOT_A_BOT =
            new SimpleCommandExceptionType(Text.literal("That player is not a bot."));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("bot")
            .requires(src -> BotManager.getBotPermissionCheck().allows(src.getPermissions()))

            // /bot spawn [<name>]
            .then(literal("spawn")
                .executes(ctx -> spawnBot(ctx, null))
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> spawnBot(ctx, StringArgumentType.getString(ctx, "name")))))

            // /bot <name> <action>
            .then(argument("botname", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    for (ServerPlayerEntity p : BotManager.getActiveBots(ctx.getSource().getServer())) {
                        builder.suggest(p.getNameForScoreboard());
                    }
                    ServerPlayerEntity caller = ctx.getSource().getPlayer();
                    if (caller != null) builder.suggest(caller.getNameForScoreboard());
                    return builder.buildFuture();
                })
                .then(literal("attack")
                    .executes(ctx -> action(ctx, ap -> ap.start(ActionType.ATTACK, Action.continuous())))
                    .then(literal("stop")
                        .executes(ctx -> action(ctx, ap -> ap.start(ActionType.ATTACK, null))))
                    .then(literal("continuous")
                        .executes(ctx -> action(ctx, ap -> ap.start(ActionType.ATTACK, Action.continuous()))))
                    .then(literal("interval")
                        .then(argument("ticks", IntegerArgumentType.integer(1))
                            .executes(ctx -> action(ctx, ap -> ap.start(ActionType.ATTACK,
                                Action.interval(IntegerArgumentType.getInteger(ctx, "ticks"))))))))
                .then(literal("use")
                    .executes(ctx -> action(ctx, ap -> ap.start(ActionType.USE, Action.continuous())))
                    .then(literal("stop")
                        .executes(ctx -> action(ctx, ap -> ap.start(ActionType.USE, null))))
                    .then(literal("continuous")
                        .executes(ctx -> action(ctx, ap -> ap.start(ActionType.USE, Action.continuous()))))
                    .then(literal("interval")
                        .then(argument("ticks", IntegerArgumentType.integer(1))
                            .executes(ctx -> action(ctx, ap -> ap.start(ActionType.USE,
                                Action.interval(IntegerArgumentType.getInteger(ctx, "ticks"))))))))
                .then(literal("jump")
                    .executes(ctx -> action(ctx, ap -> ap.start(ActionType.JUMP, Action.continuous())))
                    .then(literal("stop")
                        .executes(ctx -> action(ctx, ap -> ap.start(ActionType.JUMP, null)))))
                .then(literal("drop")
                    .executes(ctx -> drop(ctx, false)))
                .then(literal("dropstack")
                    .executes(ctx -> drop(ctx, true)))
                .then(literal("swaphands")
                    .executes(ctx -> action(ctx, ap -> ap.start(ActionType.SWAP_HANDS, Action.once()))))
                .then(literal("sneak")
                    .executes(ctx -> action(ctx, ap -> ap.setSneaking(true)))
                    .then(literal("stop")
                        .executes(ctx -> action(ctx, ap -> ap.setSneaking(false)))))
                .then(literal("sprint")
                    .executes(ctx -> action(ctx, ap -> ap.setSprinting(true)))
                    .then(literal("stop")
                        .executes(ctx -> action(ctx, ap -> ap.setSprinting(false)))))
                .then(literal("stop")
                    .executes(ctx -> action(ctx, EntityPlayerActionPack::stopAll)))
                .then(literal("kill")
                    .executes(BotCommand::killBot))
                .then(literal("look")
                    .then(literal("north").executes(ctx -> action(ctx, ap -> ap.look(Direction.NORTH))))
                    .then(literal("south").executes(ctx -> action(ctx, ap -> ap.look(Direction.SOUTH))))
                    .then(literal("east") .executes(ctx -> action(ctx, ap -> ap.look(Direction.EAST))))
                    .then(literal("west") .executes(ctx -> action(ctx, ap -> ap.look(Direction.WEST))))
                    .then(literal("up")   .executes(ctx -> action(ctx, ap -> ap.look(Direction.UP))))
                    .then(literal("down") .executes(ctx -> action(ctx, ap -> ap.look(Direction.DOWN))))
                    .then(argument("yaw", FloatArgumentType.floatArg(-180, 180))
                        .then(argument("pitch", FloatArgumentType.floatArg(-90, 90))
                            .executes(ctx -> action(ctx, ap -> ap.look(
                                FloatArgumentType.getFloat(ctx, "yaw"),
                                FloatArgumentType.getFloat(ctx, "pitch")))))))
                .then(literal("inventory")
                    .executes(BotCommand::openInventory))
            )
        );
    }

    // --- Spawn ---

    private static int spawnBot(CommandContext<ServerCommandSource> ctx, String rawName)
            throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();
        MinecraftServer server = src.getServer();

        String name = (rawName == null || rawName.isBlank())
                ? BotManager.nextAutoName(server)
                : rawName;

        if (!isValidName(name)) {
            src.sendError(Text.literal("Invalid name '" + name + "': must be 1-16 chars, letters/digits/underscores only."));
            return 0;
        }
        if (BotManager.isRealPlayer(name)) {
            src.sendError(Text.literal("'" + name + "' is a real player and cannot be spawned as a bot."));
            return 0;
        }
        if (server.getPlayerManager().getPlayer(name) != null) {
            src.sendError(Text.literal("A player named '" + name + "' is already online."));
            return 0;
        }

        ServerPlayerEntity executor = src.getPlayerOrThrow();
        Vec3d pos = new Vec3d(executor.getX(), executor.getY(), executor.getZ());
        ServerWorld world = src.getWorld();
        RegistryKey<World> worldKey = world.getRegistryKey();
        double yaw = executor.getYaw();

        boolean started = EntityPlayerMPFake.createFake(name, server, pos, yaw, 0, worldKey, GameMode.SURVIVAL, false);
        if (!started) {
            src.sendError(Text.literal("Failed to spawn bot '" + name + "'."));
            return 0;
        }
        
        return 1;
    }

    // --- Actions ---

    @FunctionalInterface
    private interface ActionConsumer {
        void accept(EntityPlayerActionPack ap) throws Exception;
    }

    private static int action(CommandContext<ServerCommandSource> ctx, ActionConsumer consumer)
            throws CommandSyntaxException {
        ServerPlayerEntity bot = getBot(ctx);
        EntityPlayerActionPack ap = ((ServerPlayerInterface) bot).getActionPack();
        try {
            consumer.accept(ap);
        } catch (CommandSyntaxException e) {
            throw e;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Action failed: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    private static int drop(CommandContext<ServerCommandSource> ctx, boolean fullStack)
            throws CommandSyntaxException {
        ServerPlayerEntity bot = getBot(ctx);
        ((ServerPlayerInterface) bot).getActionPack().drop(bot.getInventory().getSelectedSlot(), fullStack);
        return 1;
    }

    // --- Kill ---

    private static int killBot(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity bot = getBot(ctx);
        if (bot instanceof EntityPlayerMPFake fake) {
            fake.kill(Text.literal("Removed by /bot kill"));
        } else {
            bot.networkHandler.disconnect(Text.literal("Removed by /bot kill"));
        }
        return 1;
    }

    // --- Inventory ---

    private static int openInventory(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity opener = src.getPlayerOrThrow();
        ServerPlayerEntity bot = getBot(ctx);

        if (opener.squaredDistanceTo(bot) > 100.0) {
            src.sendError(Text.literal("Too far from bot (max 10 blocks)."));
            return 0;
        }

        opener.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, openerInv, player) -> new BotInventoryHandler(syncId, openerInv, bot),
                Text.literal("Bot: " + bot.getNameForScoreboard())
        ));
        return 1;
    }

    // --- Helpers ---

    private static ServerPlayerEntity getBot(CommandContext<ServerCommandSource> ctx)
            throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "botname");
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(name);
        if (player == null) throw BOT_NOT_FOUND.create();
        if (!(player instanceof EntityPlayerMPFake)) {
            ServerPlayerEntity caller = ctx.getSource().getPlayer();
            if (caller == null || caller != player) throw NOT_A_BOT.create();
        }
        return player;
    }

    private static boolean isValidName(String name) {
        return !name.isEmpty() && name.length() <= 16 && name.matches("[a-zA-Z0-9_]+");
    }
}
