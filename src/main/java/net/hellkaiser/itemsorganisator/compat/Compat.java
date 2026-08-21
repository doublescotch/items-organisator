package net.hellkaiser.itemsorganisator.compat;

import net.minecraftforge.fml.ModList;

/** Central optional-integration dispatch. Every branch is classload-guarded and failure-silent. */
public final class Compat {
    public static final String JEI_MODID = "jei";
    public static final String EMI_MODID = "emi";

    /**
     * Un rechargement EMI est demande mais PAS encore execute.
     *
     * EMI ne recharge son index que de facon asynchrone et, en fin de rechargement,
     * reconstruit les widgets de l'ecran ouvert depuis son propre thread: declenche
     * pendant qu'un ecran est affiche, cela modifie la liste des widgets pendant que
     * le thread de rendu l'itere => ConcurrentModificationException (crash constate
     * en v1.4.0, ~4 s apres une edition).
     *
     * On differe donc le rechargement au moment ou AUCUN ecran n'est ouvert. Effet de
     * bord bienvenu: plusieurs editions d'affilee ne provoquent qu'UN rechargement.
     */
    private static volatile boolean emiReloadPending = false;

    /**
     * EMI++ (modid emixx) affiche une barre de categories creatives au-dessus de la grille
     * EMI, alimentee par une liste CACHEE (CreativeModeTabManager.creativeModeTabs), rafraichie
     * seulement par son reload(). Sans cela, un onglet qu'on vient de vider reste affiche
     * (vide) dans EMI++ alors qu'il a disparu du menu creatif vanilla.
     *
     * reload() ne touche que des listes (pas de widgets), mais les muter pendant que la barre
     * les itere au rendu provoquerait la meme famille de crash que le reload EMI: on differe
     * donc au meme moment (aucun ecran ouvert).
     */
    private static volatile boolean emixxRefreshPending = false;

    private Compat() {}

    public static boolean jeiPresent() {
        return ModList.get().isLoaded(JEI_MODID);
    }

    public static boolean emiPresent() {
        return ModList.get().isLoaded(EMI_MODID);
    }

    /** Called (client side) after a rule change has been saved. */
    public static void afterRuleChange(boolean restrictedChanged) {
        // Un simple deplacement d'onglet peut vider l'onglet source: EMI++ doit se
        // rafraichir meme quand aucune restriction n'a change.
        if (ModList.get().isLoaded("emixx")) {
            emixxRefreshPending = true;
        }
        if (!restrictedChanged) return;

        if (jeiPresent()) {
            // JEI: retrait synchrone via son API runtime (thread principal) — sans risque.
            try {
                net.hellkaiser.itemsorganisator.jei.ItemsOrganisatorJeiPlugin.update();
            } catch (Throwable ignored) {}
        }
        if (emiPresent()) {
            emiReloadPending = true;
        }
    }

    /** Appele a chaque tick client: execute les rafraichissements en attente si c'est sur. */
    public static void flushPendingEmiReload() {
        if (!emiReloadPending && !emixxRefreshPending) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen != null || mc.level == null) return; // jamais pendant qu'un ecran est affiche

        if (emixxRefreshPending) {
            emixxRefreshPending = false;
            try {
                Class<?> mgr = Class.forName("concerrox.emixx.content.creativemodetab.CreativeModeTabManager");
                Object instance = mgr.getField("INSTANCE").get(null);
                mgr.getMethod("reload").invoke(instance);
            } catch (Throwable ignored) {
                // API interne d'EMI++: si elle bouge, sa barre se remettra a jour a son
                // prochain rechargement naturel — jamais de crash.
            }
        }
        if (emiReloadPending) {
            emiReloadPending = false;
            try {
                Class.forName("dev.emi.emi.runtime.EmiReloadManager")
                        .getMethod("reload").invoke(null);
            } catch (Throwable ignored) {
                // API interne d'EMI: si elle bouge, le filtrage s'appliquera au prochain
                // rechargement naturel (F3+T, rejoin) — jamais de crash.
            }
        }
    }
}
