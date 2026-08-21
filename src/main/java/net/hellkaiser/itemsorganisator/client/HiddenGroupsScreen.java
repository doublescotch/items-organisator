package net.hellkaiser.itemsorganisator.client;

import net.hellkaiser.itemsorganisator.compat.Compat;
import net.hellkaiser.itemsorganisator.config.OrganisatorConfig;
import net.hellkaiser.itemsorganisator.mixin.CreativeModeTabAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Right-click on the Hidden tab header: hidden entries grouped by their item's NATURAL tab
 * (a one-time scan of every CATEGORY tab's DisplayItemsGenerator = pre-filter contents).
 * One row per group: "« TabName » — N objets — tout restaurer"; clicking deletes all the
 * group's hidden entries (items return to their natural tabs, pages recalc).
 * Unresolvable entries (absent mods, unmatched items) group under "Autres" and remain
 * cleanable. Blacklist entries are NOT shown here (JSON + per-item UI only).
 */
public class HiddenGroupsScreen extends Screen {
    private record Group(String label, List<String> entries) {}

    private static final int ROW_HEIGHT = 14;
    private static final int LIST_TOP = 44;

    private final List<Group> groups = new ArrayList<>();
    private int scrollRow = 0;

    public HiddenGroupsScreen() {
        super(Component.translatable("itemsorganisator.groups.title"));
    }

    private int listWidth() {
        return Math.min(340, this.width - 40);
    }

    private int listX() {
        return (this.width - listWidth()) / 2;
    }

    private int visibleRows() {
        return Math.max(1, (this.height - 24 - LIST_TOP) / ROW_HEIGHT);
    }

    /** First CATEGORY tab (registry order, vanilla first) whose generator naturally yields the item. */
    private static Map<Item, ResourceLocation> scanNaturalTabs() {
        Map<Item, ResourceLocation> natural = new HashMap<>();
        CreativeModeTab.ItemDisplayParameters params = TabRebuilder.currentParams();
        if (params == null) return natural;
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            ResourceLocation tabId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (tabId == null || OrganisatorConfig.HIDDEN_TAB_ID.equals(tabId)) continue;
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
            try {
                CreativeModeTab.DisplayItemsGenerator gen =
                        ((CreativeModeTabAccessor) tab).itemsorganisator$getDisplayItemsGenerator();
                gen.accept(params, new CreativeModeTab.Output() {
                    @Override
                    public void accept(ItemStack stack, CreativeModeTab.TabVisibility visibility) {
                        if (stack != null && !stack.isEmpty()) {
                            natural.putIfAbsent(stack.getItem(), tabId);
                        }
                    }
                });
            } catch (Throwable ignored) {
                // a broken generator only degrades grouping (items fall into "Autres")
            }
        }
        return natural;
    }

    @Override
    protected void init() {
        groups.clear();
        scrollRow = 0;
        OrganisatorConfig cfg = OrganisatorConfig.get();
        Map<Item, ResourceLocation> natural = scanNaturalTabs();

        Map<String, List<String>> byTab = new LinkedHashMap<>();
        List<String> others = new ArrayList<>();
        for (String raw : cfg.rawHidden()) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            ResourceLocation tabId = null;
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                tabId = natural.get(BuiltInRegistries.ITEM.get(id));
            }
            if (tabId == null) {
                others.add(raw);
            } else {
                byTab.computeIfAbsent(tabId.toString(), k -> new ArrayList<>()).add(raw);
            }
        }
        for (Map.Entry<String, List<String>> e : byTab.entrySet()) {
            ResourceLocation tabId = ResourceLocation.tryParse(e.getKey());
            CreativeModeTab tab = tabId == null ? null : BuiltInRegistries.CREATIVE_MODE_TAB.get(tabId);
            String label = tab != null ? tab.getDisplayName().getString() : e.getKey();
            groups.add(new Group(label, e.getValue()));
        }
        groups.sort(Comparator.comparing(Group::label));
        if (!others.isEmpty()) {
            groups.add(new Group(
                    Component.translatable("itemsorganisator.groups.other").getString(), others));
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx);
        gfx.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);

        if (groups.isEmpty()) {
            gfx.drawCenteredString(this.font,
                    Component.translatable("itemsorganisator.groups.empty"),
                    this.width / 2, LIST_TOP + 6, 0x808080);
            super.render(gfx, mouseX, mouseY, partialTick);
            return;
        }

        int x = listX();
        int w = listWidth();
        int rows = visibleRows();
        int hoveredRow = rowAt(mouseX, mouseY);
        for (int i = 0; i < rows; i++) {
            int idx = scrollRow + i;
            if (idx >= groups.size()) break;
            Group g = groups.get(idx);
            int y = LIST_TOP + i * ROW_HEIGHT;
            if (idx == hoveredRow) {
                gfx.fill(x - 2, y - 1, x + w + 2, y + ROW_HEIGHT - 2, 0x40FFFFFF);
            }
            String text = Component.translatable("itemsorganisator.groups.row",
                    g.label(), g.entries().size()).getString();
            gfx.drawString(this.font, this.font.plainSubstrByWidth(text, w - 8), x + 4, y + 2, 0xFFB080);
        }
        if (groups.size() > rows) {
            int trackH = rows * ROW_HEIGHT;
            int thumbH = Math.max(8, trackH * rows / groups.size());
            int maxScroll = groups.size() - rows;
            int thumbY = LIST_TOP + (trackH - thumbH) * scrollRow / Math.max(1, maxScroll);
            gfx.fill(x + w + 4, LIST_TOP, x + w + 6, LIST_TOP + trackH, 0x30FFFFFF);
            gfx.fill(x + w + 4, thumbY, x + w + 6, thumbY + thumbH, 0xA0FFFFFF);
        }
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private int rowAt(double mouseX, double mouseY) {
        int x = listX();
        if (mouseX < x - 2 || mouseX > x + listWidth() + 2) return -1;
        if (mouseY < LIST_TOP || mouseY >= LIST_TOP + visibleRows() * ROW_HEIGHT) return -1;
        int idx = scrollRow + (int) ((mouseY - LIST_TOP) / ROW_HEIGHT);
        return idx < groups.size() ? idx : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int idx = rowAt(mouseX, mouseY);
            if (idx >= 0) {
                restoreGroup(groups.get(idx));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, groups.size() - visibleRows());
        scrollRow = Math.max(0, Math.min(maxScroll, scrollRow - (int) Math.signum(delta)));
        return true;
    }

    private void restoreGroup(Group group) {
        OrganisatorConfig cfg = OrganisatorConfig.get();
        boolean changed = cfg.unhideAll(group.entries());
        cfg.saveNow();
        TabRebuilder.rebuildAll();
        Compat.afterRuleChange(changed);
        ClientHooks.toast(Component.translatable("itemsorganisator.toast.title"),
                Component.translatable("itemsorganisator.toast.group_restored",
                        group.label(), group.entries().size()));
        ClientHooks.reopenCreative();
    }

    @Override
    public void onClose() {
        ClientHooks.reopenCreative();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
