package net.hellkaiser.itemsorganisator.rules;

import net.hellkaiser.itemsorganisator.config.OrganisatorConfig;
import net.hellkaiser.itemsorganisator.mixin.CreativeModeTabAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Removal side of the rules — runs at the tail of CreativeModeTab#buildContents. */
public final class RuleEngine {
    private RuleEngine() {}

    public static void filterTab(CreativeModeTab tab) {
        ResourceLocation tabId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        if (tabId == null) return;
        boolean isHiddenTab = OrganisatorConfig.HIDDEN_TAB_ID.equals(tabId);
        OrganisatorConfig cfg = OrganisatorConfig.get();
        boolean tabFullyMoved = cfg.activeTabMoveTarget(tabId) != null;

        Predicate<ItemStack> remove = stack -> {
            Item item = stack.getItem();
            // blacklisted items vanish from EVERY tab, the Hidden tab included
            if (cfg.isBlacklisted(item)) return true;
            // hidden items vanish from every tab except our Hidden tab
            if (!isHiddenTab && cfg.isHidden(item)) return true;
            // an explicit per-item rule overrides everything else
            ResourceLocation target = cfg.activeItemMoveTarget(item);
            if (target != null) return !target.equals(tabId);
            // a fully-moved source tab loses all its (remaining) content
            return tabFullyMoved;
        };

        CreativeModeTabAccessor acc = (CreativeModeTabAccessor) tab;
        acc.itemsorganisator$getDisplayItems().removeIf(remove);
        acc.itemsorganisator$getDisplayItemsSearchTab().removeIf(remove);
    }
}
