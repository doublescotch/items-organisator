package net.hellkaiser.itemsorganisator.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import net.hellkaiser.itemsorganisator.config.OrganisatorConfig;
import net.minecraft.world.item.Item;

/**
 * Integration EMI: retire les items hidden/blacklisted de l'index EMI.
 * EMI construit son propre index independamment de JEI (meme en mode "JEI compat"),
 * donc le masquage JEI seul laissait les items visibles dans la grille EMI.
 *
 * Cette classe n'est chargee que par le scan @EmiEntrypoint d'EMI — jamais sans EMI.
 * Execute a chaque (re)construction de l'index EMI; les changements en cours de
 * session sont appliques via un reload EMI declenche par Compat.afterRuleChange.
 */
@EmiEntrypoint
public class ItemsOrganisatorEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.removeEmiStacks(stack -> {
            try {
                Item item = stack.getKeyOfType(Item.class);
                return item != null && OrganisatorConfig.get().isRestricted(item);
            } catch (Throwable t) {
                return false;
            }
        });
    }
}
