package net.hellkaiser.itemsorganisator;

import net.hellkaiser.itemsorganisator.config.OrganisatorConfig;
import net.hellkaiser.itemsorganisator.server.ServerEnforcement;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(OrganisatorConfig.MODID)
public class ItemsOrganisator {
    public ItemsOrganisator() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModTabs.TABS.register(modBus);
        MinecraftForge.EVENT_BUS.register(new ServerEnforcement());
        if (FMLEnvironment.dist.isClient()) {
            // Spec Forge des gestes d'edition: Configured (ou tout ecran generique)
            // en construit automatiquement l'UI in-game.
            net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                    net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                    net.hellkaiser.itemsorganisator.config.ClientControlsConfig.SPEC,
                    OrganisatorConfig.MODID + "-controls.toml");
            net.hellkaiser.itemsorganisator.client.ClientEvents.init(modBus);
        }
    }
}
