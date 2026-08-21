package net.hellkaiser.itemsorganisator.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.client.gui.CreativeTabsScreenPage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeModeInventoryScreenInvoker {
    /** Vanilla tab-header hit detection; coordinates are relative to guiLeft/guiTop. */
    @Invoker("checkTabClicked")
    boolean itemsorganisator$checkTabClicked(CreativeModeTab tab, double relMouseX, double relMouseY);

    /** Vanilla static: the currently selected tab (persists across screen instances). */
    @Accessor("selectedTab")
    static CreativeModeTab itemsorganisator$getSelectedTab() {
        throw new AssertionError();
    }

    // Forge-added (patched-in) fields keep their plain names in production: remap=false.

    @Accessor(value = "pages", remap = false)
    List<CreativeTabsScreenPage> itemsorganisator$getPages();

    @Accessor(value = "currentPage", remap = false)
    void itemsorganisator$setCurrentPage(CreativeTabsScreenPage page);
}
