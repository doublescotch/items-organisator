package net.hellkaiser.itemsorganisator.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.function.Predicate;

/**
 * Purge des inventaires IMBRIQUES: sacs a dos, pochettes, shulkers, conteneurs portables.
 *
 * Approche volontairement AGNOSTIQUE, deux voies complementaires:
 *
 * 1. capability Forge `ITEM_HANDLER` posee sur l'ItemStack — c'est la voie que suivent
 *    Sophisticated Backpacks (via son ICapabilityProvider), les sacs lies a TACZ et la
 *    plupart des conteneurs portables de l'ecosysteme Forge, presents ou futurs;
 * 2. NBT `BlockEntityTag/Items` — les shulkers vanilla et les block-items a inventaire
 *    n'exposent AUCUNE capability tant qu'ils sont a l'etat d'objet, leur contenu ne vit
 *    que dans le NBT.
 *
 * Profondeur bornee (sac dans un sac) pour eviter tout cycle et garder un cout fixe.
 */
public final class NestedInventories {
    private NestedInventories() {}

    /** Sac dans un sac: suffisant en pratique, et borne le cout. */
    private static final int MAX_DEPTH = 2;

    private static final String BLOCK_ENTITY_TAG = "BlockEntityTag";
    private static final String ITEMS_TAG = "Items";

    /**
     * Purge recursivement le contenu d'un objet-conteneur.
     * @return true si quelque chose a ete retire.
     */
    public static boolean purgeInside(ItemStack container, Predicate<ItemStack> shouldPurge) {
        return purge(container, shouldPurge, 0);
    }

    private static boolean purge(ItemStack container, Predicate<ItemStack> shouldPurge, int depth) {
        if (container.isEmpty() || depth >= MAX_DEPTH) return false;
        boolean changed = purgeCapability(container, shouldPurge, depth);
        if (purgeBlockEntityNbt(container, shouldPurge, depth)) changed = true;
        return changed;
    }

    // ------------------------------------------------------- voie 1: capability

    private static boolean purgeCapability(ItemStack container, Predicate<ItemStack> shouldPurge, int depth) {
        try {
            return container.getCapability(ForgeCapabilities.ITEM_HANDLER).map(handler -> {
                boolean changed = false;
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (stack.isEmpty()) continue;

                    if (shouldPurge.test(stack)) {
                        if (remove(handler, slot)) changed = true;
                    } else if (purge(stack, shouldPurge, depth + 1)) {
                        changed = true; // objet garde, mais son contenu a ete nettoye
                    }
                }
                return changed;
            }).orElse(false);
        } catch (Throwable t) {
            return false; // un conteneur exotique ne doit jamais casser le balayage
        }
    }

    /** Retrait, en privilegiant l'ecriture directe et sinon l'extraction. */
    private static boolean remove(IItemHandler handler, int slot) {
        try {
            if (handler instanceof IItemHandlerModifiable modifiable) {
                modifiable.setStackInSlot(slot, ItemStack.EMPTY);
                return true;
            }
            ItemStack extracted = handler.extractItem(slot, Integer.MAX_VALUE, false);
            return !extracted.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------- voie 2: NBT block-entity

    /**
     * Shulkers et block-items a inventaire: le contenu est une ListTag d'ItemStack
     * serialises sous BlockEntityTag/Items. On reecrit la liste en sautant les entrees
     * restreintes; les autres sont conservees telles quelles (Slot inclus), donc aucune
     * donnee tierce n'est perdue.
     */
    private static boolean purgeBlockEntityNbt(ItemStack container, Predicate<ItemStack> shouldPurge, int depth) {
        try {
            CompoundTag tag = container.getTag();
            if (tag == null || !tag.contains(BLOCK_ENTITY_TAG, Tag.TAG_COMPOUND)) return false;
            CompoundTag blockEntity = tag.getCompound(BLOCK_ENTITY_TAG);
            if (!blockEntity.contains(ITEMS_TAG, Tag.TAG_LIST)) return false;

            ListTag items = blockEntity.getList(ITEMS_TAG, Tag.TAG_COMPOUND);
            ListTag kept = new ListTag();
            boolean changed = false;

            for (int i = 0; i < items.size(); i++) {
                CompoundTag entry = items.getCompound(i);
                ItemStack stack = ItemStack.of(entry);
                if (stack.isEmpty()) continue;

                if (shouldPurge.test(stack)) {
                    changed = true;
                    continue;
                }
                if (purge(stack, shouldPurge, depth + 1)) {
                    // le contenu imbrique a change: reserialiser en gardant l'emplacement
                    CompoundTag rewritten = new CompoundTag();
                    stack.save(rewritten);
                    if (entry.contains("Slot")) rewritten.putByte("Slot", entry.getByte("Slot"));
                    kept.add(rewritten);
                    changed = true;
                } else {
                    kept.add(entry);
                }
            }

            if (changed) blockEntity.put(ITEMS_TAG, kept);
            return changed;
        } catch (Throwable t) {
            return false;
        }
    }
}
