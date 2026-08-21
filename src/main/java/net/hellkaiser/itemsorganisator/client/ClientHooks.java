package net.hellkaiser.itemsorganisator.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;

/** Client-only helpers (never classloaded on a dedicated server). */
public final class ClientHooks {
    private ClientHooks() {}

    /** L'ecran creatif d'ou vient l'edition en cours (repris tel quel apres l'edition). */
    private static CreativeModeInventoryScreen lastCreativeScreen;

    public static void rememberCreative(CreativeModeInventoryScreen screen) {
        lastCreativeScreen = screen;
    }

    /** Hidden tab contents: local player in creative AND permission level >= 2. */
    public static boolean canSeeHidden() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isCreative() && mc.player.hasPermissions(2);
    }

    /**
     * Retourne a l'ecran creatif d'origine. setScreen re-invoque init() sur l'instance:
     * pages re-partitionnees et contenu du menu recopie depuis les collections — que
     * TabRebuilder vient de reconstruire — donc rafraichissement EN PLACE, sans
     * fermeture/reouverture manuelle de l'inventaire.
     */
    public static void reopenCreative() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            mc.setScreen(null);
            return;
        }
        CreativeModeInventoryScreen target = lastCreativeScreen;
        lastCreativeScreen = null;
        if (target == null) {
            target = new CreativeModeInventoryScreen(
                    mc.player,
                    mc.player.connection.enabledFeatures(),
                    mc.options.operatorItemsTab().get());
        }
        mc.setScreen(target);
    }

    public static void toast(Component title, Component message) {
        if (!net.hellkaiser.itemsorganisator.config.ClientControlsConfig.showToasts()) return;
        Minecraft mc = Minecraft.getInstance();
        SystemToast.add(mc.getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION, title, message);
    }
}
