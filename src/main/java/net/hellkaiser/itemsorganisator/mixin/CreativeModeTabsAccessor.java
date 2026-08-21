package net.hellkaiser.itemsorganisator.mixin;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreativeModeTabs.class)
public interface CreativeModeTabsAccessor {
    /**
     * Vanilla short-circuits tryRebuildTabContents when the cached ItemDisplayParameters are
     * unchanged. Setting the cache to null FORCES the next call to run the full vanilla
     * buildAllTabContents — the exact code path of a world join.
     */
    @Accessor("CACHED_PARAMETERS")
    static void itemsorganisator$setCachedParameters(CreativeModeTab.ItemDisplayParameters params) {
        throw new AssertionError();
    }

    @Accessor("CACHED_PARAMETERS")
    static CreativeModeTab.ItemDisplayParameters itemsorganisator$getCachedParameters() {
        throw new AssertionError();
    }
}
