package net.hellkaiser.itemsorganisator.server;

import net.hellkaiser.itemsorganisator.config.OrganisatorConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Two-tier enforcement:
 *
 * BLACKLISTED — total eradication, no exemption whatsoever (creative admins included):
 * purged from every inventory and container, drops discarded, pickup blocked, use/attack
 * blocked for everyone.
 *
 * HIDDEN — inventory-only for creative admins: they keep and use the item normally, but can
 * never put it into the world or hand it over — drops are discarded unconditionally, block
 * placement is blocked for everyone, containers are stripped for every opener. Non-creative
 * players: full purge/block like a blacklisted item (minus regexes).
 */
public final class ServerEnforcement {

    private static boolean exempt(Player player) {
        return player.isCreative() || player.isSpectator();
    }

    /** Should this stack be purged out of this player's own inventory? */
    private static boolean purgeFromPlayer(Player player, ItemStack stack, OrganisatorConfig cfg) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (cfg.isBlacklisted(item)) return true;                 // everyone, creative included
        return !exempt(player) && cfg.isHidden(item);             // hidden: creative keeps it
    }

    // ------------------------------------------------------------------ inventories

    /** Curios est present: ses emplacements font partie de l'equipement du joueur. */
    private static final boolean CURIOS =
            net.minecraftforge.fml.ModList.get().isLoaded("curios");

    /**
     * Balayage de l'inventaire du joueur toutes les 20 ticks, et des inventaires
     * IMBRIQUES (sacs a dos, pochettes) toutes les 100 ticks.
     *
     * Ce dedoublement de cadence est volontaire: un objet range au fond d'un sac
     * n'est pas immediatement utilisable, il ne merite donc pas un balayage par
     * seconde — d'autant que descendre dans les conteneurs coute plus cher que de
     * parcourir 41 emplacements plats.
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide() || player.tickCount % 20 != 0) return;
        OrganisatorConfig cfg = OrganisatorConfig.get();
        if (cfg.restrictedItems().isEmpty()) return;
        try {
            java.util.function.Predicate<ItemStack> rule = stack -> purgeFromPlayer(player, stack, cfg);
            boolean deepPass = player.tickCount % 100 == 0;

            var inv = player.getInventory();
            boolean changed = false;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (purgeFromPlayer(player, stack, cfg)) {
                    inv.setItem(i, ItemStack.EMPTY);
                    changed = true;
                } else if (deepPass && NestedInventories.purgeInside(stack, rule)) {
                    changed = true; // le sac reste, son contenu restreint part
                }
            }

            // Emplacements Curios: meme regle que l'inventaire, sacs portes compris.
            if (CURIOS && CuriosPurge.purge(player, rule)) {
                changed = true;
            }

            if (changed) player.containerMenu.broadcastChanges();
        } catch (Throwable ignored) {}
    }

    /**
     * Containers opened by ANY player (chests, barrels, ...): hidden and blacklisted items are
     * stripped from the container part — a hidden item must never rest in a world container,
     * even when a creative admin parks it there. The player's OWN inventory slots of the menu
     * are left to the tick purge above (creative admins keep hidden items in their inventory).
     */
    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        OrganisatorConfig cfg = OrganisatorConfig.get();
        if (cfg.restrictedItems().isEmpty()) return;
        try {
            AbstractContainerMenu menu = event.getContainer();
            boolean changed = false;
            for (Slot slot : menu.slots) {
                if (slot.container instanceof Inventory) continue; // player's own inventory part
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) continue;
                if (cfg.isRestricted(stack.getItem())) {
                    slot.set(ItemStack.EMPTY);
                    changed = true;
                } else if (NestedInventories.purgeInside(
                        stack, nested -> !nested.isEmpty() && cfg.isRestricted(nested.getItem()))) {
                    // un sac ou un shulker pose dans un coffre: le contenant reste, pas son contenu
                    slot.setChanged();
                    changed = true;
                }
            }
            if (changed) menu.broadcastChanges();
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------ world side

    /** Ground pickup: blocked when the item would be purged from that player anyway. */
    @SubscribeEvent
    public void onItemPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (purgeFromPlayer(player, event.getItem().getItem(), OrganisatorConfig.get())) {
            event.setCanceled(true);
        }
    }

    /**
     * Drop-discard, unconditional for BOTH tiers: any restricted ItemEntity entering the world
     * (player drop — creative admins included —, mob drop, block drop, dispenser, chunk load)
     * is removed. This is what keeps hidden items strictly inventory-bound.
     */
    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;
        if (OrganisatorConfig.get().isRestricted(stack.getItem())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ use / attack

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        OrganisatorConfig cfg = OrganisatorConfig.get();
        if (cfg.isBlacklisted(stack.getItem())) {
            event.setCanceled(true);                               // everyone
        } else if (cfg.isHidden(stack.getItem()) && !exempt(player)) {
            event.setCanceled(true);                               // hidden: creative may use
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        OrganisatorConfig cfg = OrganisatorConfig.get();
        Item item = stack.getItem();
        if (cfg.isBlacklisted(item)) {
            event.setCanceled(true);                               // everyone
        } else if (cfg.isHidden(item)) {
            if (!exempt(player)) {
                event.setCanceled(true);                           // non-creative: no use at all
            } else if (item instanceof BlockItem) {
                event.setCanceled(true);                           // creative admin: no PLACEMENT
                if (!player.level().isClientSide()) {
                    player.displayClientMessage(
                            Component.translatable("itemsorganisator.msg.no_place"), true);
                }
            }
        }
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;
        OrganisatorConfig cfg = OrganisatorConfig.get();
        if (cfg.isBlacklisted(stack.getItem())) {
            event.setCanceled(true);                               // everyone
        } else if (cfg.isHidden(stack.getItem()) && !exempt(player)) {
            event.setCanceled(true);                               // hidden: creative may attack
        }
    }
}
