package net.hellkaiser.itemsorganisator.client;

import net.hellkaiser.itemsorganisator.mixin.CreativeModeTabsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * Application a chaud d'un changement de regles cote client.
 *
 * TROIS etapes, toutes necessaires :
 * 1. Invalider le cache statique vanilla (CreativeModeTabs.CACHED_PARAMETERS) sinon
 *    tryRebuildTabContents court-circuite.
 * 2. Invalider la memoisation PAR ONGLET de ModernFix (perf.memoize_creative_tab_build,
 *    champ injecte mfix$oldParameters) sinon le vrai build — et donc nos regles — n'est
 *    jamais re-execute (cause racine du bug "UI stale jusqu'au relog").
 * 3. Reconstruire SYNCHRONEMENT via le chemin vanilla complet, pour que l'ecran
 *    re-initialise ensuite copie des collections deja fraiches (pas de dependance au
 *    constructeur d'ecran ni au containerTick).
 */
public final class TabRebuilder {
    private TabRebuilder() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("itemsorganisator");
    private static Field mfixParamsField;
    private static boolean mfixLookupDone = false;

    public static void rebuildAll() {
        try {
            CreativeModeTabsAccessor.itemsorganisator$setCachedParameters(null);
        } catch (Throwable t) {
            LOGGER.warn("CACHED_PARAMETERS accessor failed: {}", t.toString());
        }
        clearModernFixMemo();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            boolean opTab = mc.player.canUseGameMasterBlocks() && mc.options.operatorItemsTab().get();
            try {
                CreativeModeTabs.tryRebuildTabContents(
                        mc.player.connection.enabledFeatures(), opTab, mc.level.registryAccess());
            } catch (Throwable t) {
                LOGGER.warn("synchronous tab rebuild failed: {}", t.toString());
            }
        }
    }

    private static void clearModernFixMemo() {
        if (!mfixLookupDone) {
            mfixLookupDone = true;
            try {
                mfixParamsField = CreativeModeTab.class.getDeclaredField("mfix$oldParameters");
                mfixParamsField.setAccessible(true);
                LOGGER.debug("ModernFix memoize_creative_tab_build detected — memo buster active");
            } catch (Throwable t) {
                mfixParamsField = null; // ModernFix absent ou option desactivee
            }
        }
        if (mfixParamsField == null) return;
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            try {
                mfixParamsField.set(tab, null);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Parametres d'affichage du contexte courant (null hors monde) — utilise par le scan de groupes. */
    public static CreativeModeTab.ItemDisplayParameters currentParams() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        boolean opTab = mc.player.canUseGameMasterBlocks() && mc.options.operatorItemsTab().get();
        return new CreativeModeTab.ItemDisplayParameters(
                mc.player.connection.enabledFeatures(), opTab, mc.level.registryAccess());
    }
}
