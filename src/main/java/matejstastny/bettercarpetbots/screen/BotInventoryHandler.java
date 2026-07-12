package matejstastny.bettercarpetbots.screen;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class BotInventoryHandler extends ScreenHandler {

    // 45 container slots (5 rows × 9):
    //   Rows 0-3 (slots  0-35): bot main inv [9..35] then hotbar [0..8]
    //   Row  4   (slots 36-40): helmet, chestplate, leggings, boots, offhand
    //   Row  4   (slots 41-44): filler (gray glass panes, locked)
    // Slots 45-80: opener's own inventory (standard chest layout)

    // Screen slot indices for armor + offhand (used for visual glass pane injection)
    private static final int[] ARMOR_OFFHAND_SLOTS = {36, 37, 38, 39, 40};

    private final ServerPlayerEntity bot;
    private final ServerPlayerEntity opener;
    private final SimpleInventory fillerInv;
    private final ItemStack[] armorOffhandPlaceholders;

    public BotInventoryHandler(int syncId, PlayerInventory openerInv, ServerPlayerEntity bot) {
        super(ScreenHandlerType.GENERIC_9X5, syncId);
        this.bot = bot;
        this.opener = (ServerPlayerEntity) openerInv.player;
        this.fillerInv = new SimpleInventory(4);

        this.armorOffhandPlaceholders = new ItemStack[]{
            makePlaceholder(Items.RED_STAINED_GLASS_PANE,    "Helmet"),
            makePlaceholder(Items.ORANGE_STAINED_GLASS_PANE, "Chestplate"),
            makePlaceholder(Items.YELLOW_STAINED_GLASS_PANE, "Leggings"),
            makePlaceholder(Items.GREEN_STAINED_GLASS_PANE,  "Boots"),
            makePlaceholder(Items.CYAN_STAINED_GLASS_PANE,   "Offhand"),
        };

        // Point slots directly at the bot's live inventory — no proxy.
        // This ensures the screen handler's own tracking picks up any changes
        // (items picked up by the bot, etc.) automatically every tick.
        PlayerInventory botInv = bot.getInventory();

        // Rows 0-2: non-hotbar (botInv slots 9-35)
        for (int i = 0; i < 27; i++) {
            addSlot(new Slot(botInv, 9 + i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
        }
        // Row 3: hotbar (botInv slots 0-8)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(botInv, col, 8 + col * 18, 72));
        }

        // Row 4: armor (type-filtered) + offhand + filler
        addSlot(new ArmorSlot(botInv, 39,  8, 90, EquipmentSlot.HEAD));
        addSlot(new ArmorSlot(botInv, 38, 26, 90, EquipmentSlot.CHEST));
        addSlot(new ArmorSlot(botInv, 37, 44, 90, EquipmentSlot.LEGS));
        addSlot(new ArmorSlot(botInv, 36, 62, 90, EquipmentSlot.FEET));
        addSlot(new Slot(botInv, 40, 80, 90));  // offhand: any item
        for (int i = 0; i < 4; i++) {
            addSlot(new FillerSlot(fillerInv, i, 98 + i * 18, 90));
        }

        // Opener's own inventory (3 rows + hotbar, matching GENERIC_9X5 layout)
        int yShift = 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(openerInv, 9 + col + row * 9, 8 + col * 18, 103 + row * 18 + yShift));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(openerInv, col, 8 + col * 18, 161 + yShift));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return !bot.isRemoved() && player.squaredDistanceTo(bot) <= 100.0;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        fillerInv.onClose(player);
    }

    /**
     * After the vanilla sync sends real slot contents, visually replace empty
     * armor/offhand slots with glass pane placeholders on the client.
     *
     * Keeping getStack() empty means vanilla insertion logic works normally —
     * the client sees the placeholder only as a visual hint.
     */
    @Override
    public void sendContentUpdates() {
        super.sendContentUpdates();
        for (int i = 0; i < ARMOR_OFFHAND_SLOTS.length; i++) {
            if (this.slots.get(ARMOR_OFFHAND_SLOTS[i]).getStack().isEmpty()) {
                opener.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(
                    this.syncId, this.nextRevision(),
                    ARMOR_OFFHAND_SLOTS[i], armorOffhandPlaceholders[i]));
            }
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasStack()) return ItemStack.EMPTY;

        ItemStack stack = slot.getStack();
        ItemStack result = stack.copy();

        if (index < 41) {
            // Bot slot → opener inventory
            if (!insertItem(stack, 45, 81, true)) return ItemStack.EMPTY;
        } else if (index >= 45) {
            // Opener inventory → bot slots
            if (!insertItem(stack, 0, 41, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY; // filler slots 41-44
        }

        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();
        return result;
    }

    private static ItemStack makePlaceholder(Item item, String label) {
        ItemStack pane = new ItemStack(item);
        pane.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal(label).styled(s -> s.withColor(Formatting.WHITE).withItalic(false)));
        return pane;
    }

    /** Armor slot that restricts insertion to the correct armor type via EQUIPPABLE component. */
    private static class ArmorSlot extends Slot {
        private final EquipmentSlot armorSlot;

        ArmorSlot(Inventory inv, int index, int x, int y, EquipmentSlot armorSlot) {
            super(inv, index, x, y);
            this.armorSlot = armorSlot;
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
            return equippable != null && equippable.slot() == armorSlot;
        }
    }

    /** Locked slot that always shows a gray glass pane; blocks all interaction. */
    private static class FillerSlot extends Slot {
        private final ItemStack pane;

        FillerSlot(Inventory inv, int index, int x, int y) {
            super(inv, index, x, y);
            this.pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
            this.pane.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(" ").styled(s -> s.withItalic(false)));
        }

        @Override public ItemStack getStack()                           { return pane; }
        @Override public boolean hasStack()                             { return false; }
        @Override public boolean canInsert(ItemStack stack)             { return false; }
        @Override public boolean canTakeItems(PlayerEntity player)      { return false; }
    }
}
