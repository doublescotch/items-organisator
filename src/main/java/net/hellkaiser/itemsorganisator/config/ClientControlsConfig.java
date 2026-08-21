package net.hellkaiser.itemsorganisator.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Config CLIENT des gestes d'edition (spec Forge standard).
 *
 * Le mod n'a pas de raccourcis clavier: ses commandes sont des gestes SOURIS sur
 * l'ecran creatif. Ils sont exposes ici pour etre reglables en jeu — Configured
 * (et tout ecran de config generique) construit automatiquement l'UI a partir de
 * cette spec, aucune integration a ecrire.
 *
 * Les regles elles-memes restent dans itemsorganisator.json (partage client/serveur);
 * ce fichier ne contient QUE de l'ergonomie locale.
 */
public final class ClientControlsConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue EDITING_ENABLED;
    public static final ForgeConfigSpec.EnumValue<MouseButton> EDIT_BUTTON;
    public static final ForgeConfigSpec.EnumValue<Modifier> ITEM_MODIFIER;
    public static final ForgeConfigSpec.BooleanValue SHOW_TOASTS;
    public static final ForgeConfigSpec.BooleanValue HIDDEN_TAB_LAST;

    /** Bouton de souris declenchant l'edition. */
    public enum MouseButton {
        LEFT(0), MIDDLE(2), RIGHT(1);

        public final int code;

        MouseButton(int code) {
            this.code = code;
        }
    }

    /** Touche a maintenir pour viser un OBJET plutot que l'onglet entier. */
    public enum Modifier {
        SHIFT, CTRL, ALT, NONE
    }

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment(
                "Items Organisator — editing gestures (client only).",
                "The rules themselves live in config/itemsorganisator.json.",
                "Gestes d'edition (client uniquement). Les regles vivent dans itemsorganisator.json."
        ).push("controls");

        EDITING_ENABLED = b
                .comment(
                        "Enable in-game editing gestures in the creative screen.",
                        "Turn this off to ship a finished pack: rules keep applying, but players cannot edit them.",
                        "Desactiver pour livrer un pack fini: les regles s'appliquent toujours, mais plus d'edition."
                )
                .define("editingEnabled", true);

        EDIT_BUTTON = b
                .comment(
                        "Mouse button used to open the editing menus.",
                        "Bouton de souris ouvrant les menus d'edition."
                )
                .defineEnum("editButton", MouseButton.RIGHT);

        ITEM_MODIFIER = b
                .comment(
                        "Modifier held to target a single ITEM instead of the whole tab.",
                        "NONE means: over an item = item, over a tab header = tab.",
                        "Touche maintenue pour viser un OBJET au lieu de l'onglet entier."
                )
                .defineEnum("itemModifier", Modifier.SHIFT);

        SHOW_TOASTS = b
                .comment(
                        "Show a confirmation toast after each rule change.",
                        "Afficher une notification apres chaque changement de regle."
                )
                .define("showToasts", true);

        HIDDEN_TAB_LAST = b
                .comment(
                        "Force the Hidden tab to be the very last creative tab.",
                        "Forcer l'onglet Masque en toute derniere position."
                )
                .define("hiddenTabLast", true);

        b.pop();
        SPEC = b.build();
    }

    private ClientControlsConfig() {}

    public static boolean editingEnabled() {
        try {
            return EDITING_ENABLED.get();
        } catch (Throwable t) {
            return true; // config pas encore chargee: comportement par defaut
        }
    }

    public static int editButtonCode() {
        try {
            return EDIT_BUTTON.get().code;
        } catch (Throwable t) {
            return 1;
        }
    }

    /**
     * Vrai si le geste doit viser l'OBJET survole. Avec NONE, aucun modificateur n'est
     * requis: survoler un objet suffit (le geste onglet reste accessible sur les en-tetes).
     */
    public static boolean itemGestureActive() {
        Modifier m;
        try {
            m = ITEM_MODIFIER.get();
        } catch (Throwable t) {
            m = Modifier.SHIFT;
        }
        return switch (m) {
            case SHIFT -> net.minecraft.client.gui.screens.Screen.hasShiftDown();
            case CTRL -> net.minecraft.client.gui.screens.Screen.hasControlDown();
            case ALT -> net.minecraft.client.gui.screens.Screen.hasAltDown();
            case NONE -> true;
        };
    }

    public static boolean showToasts() {
        try {
            return SHOW_TOASTS.get();
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean hiddenTabLast() {
        try {
            return HIDDEN_TAB_LAST.get();
        } catch (Throwable t) {
            return true;
        }
    }
}
