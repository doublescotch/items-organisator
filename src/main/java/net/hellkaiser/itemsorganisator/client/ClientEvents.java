package net.hellkaiser.itemsorganisator.client;

import net.hellkaiser.itemsorganisator.config.OrganisatorConfig;
import net.hellkaiser.itemsorganisator.mixin.CreativeModeInventoryScreenInvoker;
import net.hellkaiser.itemsorganisator.mixin.CreativeModeTabAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.Map;

/** Client wiring: rule insertions at tab build + the creative-screen editing gestures. */
public final class ClientEvents {
    private ClientEvents() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientEvents::onBuildTabContents);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onMousePressed);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
    }

    /** Rechargement EMI differe: uniquement quand aucun ecran n'est affiche (cf. Compat). */
    private static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        net.hellkaiser.itemsorganisator.compat.Compat.flushPendingEmiReload();
    }

    // ------------------------------------------------------------------ insertions

    private static void onBuildTabContents(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tabId = event.getTabKey().location();
        OrganisatorConfig cfg = OrganisatorConfig.get();

        if (OrganisatorConfig.HIDDEN_TAB_ID.equals(tabId) && !ClientHooks.canSeeHidden()) {
            return; // never inject anything into the Hidden tab for non-authorized players
        }

        // Per-item moves targeting this tab (TreeMap-backed => stable, sorted order).
        for (Map.Entry<ResourceLocation, ResourceLocation> e : cfg.itemMovesResolved().entrySet()) {
            if (!e.getValue().equals(tabId)) continue;
            if (!BuiltInRegistries.ITEM.containsKey(e.getKey())) continue; // mod removed: silent skip
            Item item = BuiltInRegistries.ITEM.get(e.getKey());
            if (item == Items.AIR || cfg.isRestricted(item)) continue; // hidden/blacklist win over moves
            try {
                event.accept(new ItemStack(item));
            } catch (Throwable ignored) {}
        }

        // Whole-tab moves targeting this tab: replay the source tab's own generator.
        for (Map.Entry<ResourceLocation, ResourceLocation> e : cfg.tabMovesResolved().entrySet()) {
            if (!e.getValue().equals(tabId) || e.getKey().equals(tabId)) continue;
            if (!BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(e.getKey())) continue; // silent skip
            CreativeModeTab source = BuiltInRegistries.CREATIVE_MODE_TAB.get(e.getKey());
            if (source == null) continue;
            try {
                CreativeModeTab.DisplayItemsGenerator generator =
                        ((CreativeModeTabAccessor) source).itemsorganisator$getDisplayItemsGenerator();
                generator.accept(event.getParameters(), new CreativeModeTab.Output() {
                    @Override
                    public void accept(ItemStack stack, CreativeModeTab.TabVisibility visibility) {
                        if (stack == null || stack.isEmpty() || cfg.isRestricted(stack.getItem())) return;
                        try {
                            event.accept(stack, visibility);
                        } catch (Throwable ignored) {}
                    }
                });
            } catch (Throwable ignored) {
                // a broken source-tab generator must never break the target tab
            }
        }
    }

    // ------------------------------------------------------------------ editing gestures

    private static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) return;
        if (!net.hellkaiser.itemsorganisator.config.ClientControlsConfig.editingEnabled()) return;
        if (event.getButton() != net.hellkaiser.itemsorganisator.config.ClientControlsConfig.editButtonCode()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Modificateur (defaut Maj) + clic sur un objet de la grille -> deplacer cet objet.
        if (net.hellkaiser.itemsorganisator.config.ClientControlsConfig.itemGestureActive()) {
            Slot slot = screen.getSlotUnderMouse();
            if (slot != null && slot.hasItem() && !slot.getItem().isEmpty()) {
                Item item = slot.getItem().getItem();
                if (item != Items.AIR && BuiltInRegistries.ITEM.getKey(item) != null) {
                    ClientHooks.rememberCreative(screen);
                    mc.setScreen(TabTargetSelectorScreen.forItem(item));
                    event.setCanceled(true);
                }
                return;
            }
        }

        // Right-click on a tab header -> move the whole tab (vanilla hit detection via invoker).
        double relX = event.getMouseX() - screen.getGuiLeft();
        double relY = event.getMouseY() - screen.getGuiTop();
        CreativeModeInventoryScreenInvoker invoker = (CreativeModeInventoryScreenInvoker) screen;
        // UNIQUEMENT les onglets de la PAGE AFFICHEE. Forge positionne chaque
        // onglet AU SEIN de sa page: deux onglets de pages differentes partagent
        // la meme hitbox, et tester tout le registre faisait gagner le premier
        // enregistre — systematiquement un onglet vanilla ("Blocs colores",
        // 2e du registre) a la place de l'onglet modde clique (bug in-game du
        // 2026-08-22, le risque n1 identifie des la conception).
        for (CreativeModeTab tab : screen.getCurrentPage().getVisibleTabs()) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
            boolean hit;
            try {
                hit = invoker.itemsorganisator$checkTabClicked(tab, relX, relY);
            } catch (Throwable t) {
                return; // hit detection unavailable: leave vanilla behavior untouched
            }
            if (!hit) continue;
            ResourceLocation tabId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (tabId != null) {
                ClientHooks.rememberCreative(screen);
                if (OrganisatorConfig.HIDDEN_TAB_ID.equals(tabId)) {
                    mc.setScreen(new HiddenGroupsScreen()); // grouped restore management
                } else {
                    mc.setScreen(TabTargetSelectorScreen.forTab(tabId, tab.getDisplayName()));
                }
                event.setCanceled(true);
            }
            return;
        }
    }
}
