package net.hellkaiser.itemsorganisator.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.hellkaiser.itemsorganisator.config.OrganisatorConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hides "hidden" items from the JEI ingredient list, live. Only classloaded by JEI itself
 * (annotation scan) or behind ModList.isLoaded("jei") guards.
 */
@JeiPlugin
public class ItemsOrganisatorJeiPlugin implements IModPlugin {
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(OrganisatorConfig.MODID, "jei");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        update();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** Sync JEI's ingredient list with the current hidden set (diff-based, error-silent). */
    public static void update() {
        IJeiRuntime rt = runtime;
        if (rt == null) return;
        try {
            IIngredientManager im = rt.getIngredientManager();
            Set<Item> hidden = OrganisatorConfig.get().restrictedItems(); // both tiers leave JEI

            Set<Item> present = new HashSet<>();
            for (ItemStack s : im.getAllIngredients(VanillaTypes.ITEM_STACK)) {
                present.add(s.getItem());
            }
            List<ItemStack> toRemove = new ArrayList<>();
            for (Item i : hidden) {
                if (present.contains(i)) toRemove.add(new ItemStack(i));
            }
            if (!toRemove.isEmpty()) {
                im.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toRemove);
            }
            // Note: items un-hidden during this session are NOT re-added to JEI here
            // (JEI would need original subtypes/NBT variants); they come back at next launch.
        } catch (Throwable ignored) {}
    }
}
