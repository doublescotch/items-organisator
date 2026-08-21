package net.hellkaiser.itemsorganisator;

import net.hellkaiser.itemsorganisator.config.OrganisatorConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The special "Hidden" creative tab (itemsorganisator:hidden). */
public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OrganisatorConfig.MODID);

    public static final RegistryObject<CreativeModeTab> HIDDEN = TABS.register("hidden", () ->
            CreativeModeTab.builder()
                    .icon(() -> new ItemStack(Items.BARRIER))
                    .title(Component.translatable("itemGroup." + OrganisatorConfig.MODID + ".hidden"))
                    .displayItems((params, out) -> {
                        // Contents only for a local creative player with permission level >= 2.
                        if (!FMLEnvironment.dist.isClient()) return;
                        if (!net.hellkaiser.itemsorganisator.client.ClientHooks.canSeeHidden()) return;
                        for (Item item : OrganisatorConfig.get().hiddenItems()) {
                            try {
                                out.accept(item);
                            } catch (Throwable ignored) {}
                        }
                    })
                    .build());

    private ModTabs() {}
}
