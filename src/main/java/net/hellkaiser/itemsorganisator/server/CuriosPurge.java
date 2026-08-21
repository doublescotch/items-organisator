package net.hellkaiser.itemsorganisator.server;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.function.Predicate;

/**
 * Purge des emplacements Curios (anneaux, ceinture, dos, ...).
 *
 * Ces emplacements font partie de l'equipement du joueur: ils suivent donc la meme
 * regle que son inventaire — les objets masques y restent pour un admin en creatif,
 * la liste noire n'epargne personne.
 *
 * Classe chargee UNIQUEMENT si le modid "curios" est present (garde dans ServerEnforcement).
 */
public final class CuriosPurge {
    private CuriosPurge() {}

    /** @return true si au moins un objet a ete retire. */
    public static boolean purge(Player player, Predicate<ItemStack> shouldPurge) {
        try {
            return CuriosApi.getCuriosInventory(player).map(inventory -> {
                boolean changed = false;
                IItemHandlerModifiable equipped = inventory.getEquippedCurios();
                if (equipped == null) return false;
                for (int slot = 0; slot < equipped.getSlots(); slot++) {
                    ItemStack stack = equipped.getStackInSlot(slot);
                    if (stack.isEmpty()) continue;

                    if (shouldPurge.test(stack)) {
                        equipped.setStackInSlot(slot, ItemStack.EMPTY);
                        changed = true;
                    } else if (NestedInventories.purgeInside(stack, shouldPurge)) {
                        changed = true; // un sac porte en Curios peut contenir des objets restreints
                    }
                }
                return changed;
            }).orElse(false);
        } catch (Throwable t) {
            return false;
        }
    }
}
