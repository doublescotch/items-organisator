package net.hellkaiser.itemsorganisator.client;

import net.hellkaiser.itemsorganisator.compat.Compat;
import net.hellkaiser.itemsorganisator.config.OrganisatorConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Target-tab picker: title = what is being moved, search box, scrollable list of every
 * registered CATEGORY creative tab (vanilla + modded), plus two pinned entries:
 * "Hidden" and "remove existing rule". Click = write rule (atomic JSON save), live tab
 * rebuild, toast, back to the creative screen. ESC = cancel.
 */
public class TabTargetSelectorScreen extends Screen {
    private enum Mode { ITEM, TAB }

    private enum Kind { HEADER, INCOMING, HIDDEN, BLACKLIST, CANCEL, TAB }

    private record Entry(Kind kind, ResourceLocation targetTab, Component label, String filterText) {}

    private static final int ROW_HEIGHT = 14;
    private static final int LIST_TOP = 62;

    private final Mode mode;
    private final Item item;                 // ITEM mode
    private final ResourceLocation itemId;   // ITEM mode
    private final ResourceLocation sourceTabId; // TAB mode

    private EditBox searchBox;
    private final List<Entry> pinned = new ArrayList<>();
    private final List<Entry> tabs = new ArrayList<>();
    private List<Entry> filtered = new ArrayList<>();
    private int scrollRow = 0;

    private TabTargetSelectorScreen(Component title, Mode mode, Item item, ResourceLocation itemId, ResourceLocation sourceTabId) {
        super(title);
        this.mode = mode;
        this.item = item;
        this.itemId = itemId;
        this.sourceTabId = sourceTabId;
    }

    public static TabTargetSelectorScreen forItem(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        Component title = Component.translatable("itemsorganisator.selector.title.item",
                item.getDescription().getString() + " (" + id + ")");
        return new TabTargetSelectorScreen(title, Mode.ITEM, item, id, null);
    }

    public static TabTargetSelectorScreen forTab(ResourceLocation tabId, Component tabName) {
        Component title = Component.translatable("itemsorganisator.selector.title.tab",
                tabName.getString() + " (" + tabId + ")");
        return new TabTargetSelectorScreen(title, Mode.TAB, null, null, tabId);
    }

    private int listWidth() {
        return Math.min(340, this.width - 40);
    }

    private int listX() {
        return (this.width - listWidth()) / 2;
    }

    private int listBottom() {
        return this.height - 24;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - LIST_TOP) / ROW_HEIGHT);
    }

    @Override
    protected void init() {
        searchBox = new EditBox(this.font, listX(), 36, listWidth(), 16,
                Component.translatable("itemsorganisator.selector.search"));
        searchBox.setHint(Component.translatable("itemsorganisator.selector.search"));
        searchBox.setResponder(s -> refilter());
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        pinned.clear();
        // "Règles entrantes" — tabMoves rules whose TARGET is this tab, cancellable where
        // their effect is visible (the source tab's own header has vanished, being empty).
        if (mode == Mode.TAB) {
            OrganisatorConfig cfg = OrganisatorConfig.get();
            List<Entry> incoming = new ArrayList<>();
            for (var e : cfg.tabMovesResolved().entrySet()) {
                if (!e.getValue().equals(sourceTabId)) continue;
                incoming.add(new Entry(Kind.INCOMING, e.getKey(),
                        Component.translatable("itemsorganisator.selector.entry.incoming",
                                tabNameOrId(e.getKey())), ""));
            }
            if (!incoming.isEmpty()) {
                pinned.add(new Entry(Kind.HEADER, null,
                        Component.translatable("itemsorganisator.selector.incoming_header"), ""));
                pinned.addAll(incoming);
            }
        }
        pinned.add(new Entry(Kind.HIDDEN, OrganisatorConfig.HIDDEN_TAB_ID,
                Component.translatable("itemsorganisator.selector.entry.hidden"), ""));
        if (mode == Mode.ITEM) { // blacklist tier is per-item only (regexes via the JSON)
            pinned.add(new Entry(Kind.BLACKLIST, null,
                    Component.translatable("itemsorganisator.selector.entry.blacklist"), ""));
        }
        pinned.add(new Entry(Kind.CANCEL, null,
                Component.translatable("itemsorganisator.selector.entry.cancel"), ""));

        tabs.clear();
        List<CreativeModeTab> all = new ArrayList<>();
        BuiltInRegistries.CREATIVE_MODE_TAB.stream()
                .filter(t -> t.getType() == CreativeModeTab.Type.CATEGORY)
                .forEach(all::add);
        all.sort(Comparator.comparing(t -> String.valueOf(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(t))));
        for (CreativeModeTab tab : all) {
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (id == null) continue;
            if (OrganisatorConfig.HIDDEN_TAB_ID.equals(id)) continue; // covered by the pinned entry
            if (mode == Mode.TAB && id.equals(sourceTabId)) continue; // exclude the source tab
            String name = tab.getDisplayName().getString();
            Component label = Component.literal(name + " ").append(
                    Component.literal("(" + id + ")").withStyle(s -> s.withColor(0x9A9A9A)));
            tabs.add(new Entry(Kind.TAB, id, label, (name + " " + id).toLowerCase(Locale.ROOT)));
        }
        refilter();
    }

    private void refilter() {
        String q = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        filtered = new ArrayList<>(pinned);
        for (Entry e : tabs) {
            if (q.isEmpty() || e.filterText().contains(q)) filtered.add(e);
        }
        scrollRow = 0;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx);
        gfx.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);

        int x = listX();
        int w = listWidth();
        int rows = visibleRows();
        int hoveredRow = rowAt(mouseX, mouseY);
        for (int i = 0; i < rows; i++) {
            int idx = scrollRow + i;
            if (idx >= filtered.size()) break;
            Entry e = filtered.get(idx);
            int y = LIST_TOP + i * ROW_HEIGHT;
            if (idx == hoveredRow) {
                gfx.fill(x - 2, y - 1, x + w + 2, y + ROW_HEIGHT - 2, 0x40FFFFFF);
            }
            int color = switch (e.kind()) {
                case HEADER -> 0x808080;
                case INCOMING -> 0xFFB080;
                case HIDDEN -> 0xFF9060;
                case BLACKLIST -> 0xFF5050;
                case CANCEL -> 0xFFD060;
                case TAB -> 0xE0E0E0;
            };
            String text = this.font.plainSubstrByWidth(e.label().getString(), w - 8);
            gfx.drawString(this.font, text, x + 4, y + 2, color);
        }
        if (filtered.size() == pinned.size() && !tabs.isEmpty()) {
            gfx.drawCenteredString(this.font, Component.translatable("itemsorganisator.selector.empty"),
                    this.width / 2, LIST_TOP + pinned.size() * ROW_HEIGHT + 6, 0x808080);
        }
        // scrollbar hint
        if (filtered.size() > rows) {
            int trackH = rows * ROW_HEIGHT;
            int thumbH = Math.max(8, trackH * rows / filtered.size());
            int maxScroll = filtered.size() - rows;
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
        return idx < filtered.size() ? idx : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int idx = rowAt(mouseX, mouseY);
            if (idx >= 0) {
                if (filtered.get(idx).kind() == Kind.HEADER) return true; // section label, inert
                choose(filtered.get(idx));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, filtered.size() - visibleRows());
        scrollRow = Math.max(0, Math.min(maxScroll, scrollRow - (int) Math.signum(delta)));
        return true;
    }

    /**
     * Whole-tab -> Hidden is expanded at click time into individual `hidden` entries for every
     * item currently displayed in that tab (explicit snapshot). This makes bulk-hide actually
     * enforced server-side and makes per-item rescue trivial (one id removed).
     * @return number of items snapshotted
     */
    private int hideWholeTabSnapshot(OrganisatorConfig cfg) {
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(sourceTabId);
        if (tab == null) return 0;
        List<ResourceLocation> ids = new ArrayList<>();
        for (net.minecraft.world.item.ItemStack stack : tab.getDisplayItems()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null && !ids.contains(id)) ids.add(id);
        }
        cfg.clearTabMove(sourceTabId); // superseded by the snapshot
        cfg.hideAll(ids);
        return ids.size();
    }

    /** Tab display name when the tab is registered, raw id otherwise (stale rules stay visible). */
    private static String tabNameOrId(ResourceLocation tabId) {
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(tabId);
        return tab != null ? tab.getDisplayName().getString() : tabId.toString();
    }

    private void choose(Entry entry) {
        OrganisatorConfig cfg = OrganisatorConfig.get();
        boolean restrictedChanged = false;
        boolean wasHidden = mode == Mode.ITEM && itemId != null
                && cfg.rawHidden().contains(itemId.toString());
        int snapshotCount = 0;

        switch (entry.kind()) {
            case HEADER -> {
                return; // inert section label
            }
            case INCOMING -> cfg.clearTabMove(entry.targetTab()); // targetTab = SOURCE tab of the rule
            case HIDDEN -> {
                if (mode == Mode.ITEM) {
                    // Garde-fou: terrain naturel / objets essentiels — refus immediat et explicite.
                    if (item != null && cfg.isProtected(item)) {
                        ClientHooks.toast(Component.translatable("itemsorganisator.toast.title"),
                                Component.translatable("itemsorganisator.toast.protected"));
                        ClientHooks.reopenCreative();
                        return;
                    }
                    restrictedChanged = cfg.hide(itemId);
                } else {
                    snapshotCount = hideWholeTabSnapshot(cfg);
                    restrictedChanged = snapshotCount > 0;
                }
            }
            case BLACKLIST -> {
                if (item != null && cfg.isProtected(item)) {
                    ClientHooks.toast(Component.translatable("itemsorganisator.toast.title"),
                            Component.translatable("itemsorganisator.toast.protected"));
                    ClientHooks.reopenCreative();
                    return;
                }
                restrictedChanged = cfg.blacklist(itemId); // ITEM mode only
            }
            case CANCEL -> {
                if (mode == Mode.ITEM) {
                    restrictedChanged = cfg.clearItemRules(itemId);
                } else {
                    cfg.clearTabMove(sourceTabId);
                }
            }
            case TAB -> {
                if (mode == Mode.ITEM) {
                    restrictedChanged = cfg.setItemMove(itemId, entry.targetTab());
                } else {
                    cfg.setTabMove(sourceTabId, entry.targetTab());
                }
            }
        }

        cfg.saveNow();
        TabRebuilder.rebuildAll();
        Compat.afterRuleChange(restrictedChanged);

        Component title = Component.translatable("itemsorganisator.toast.title");
        Component msg;
        if (entry.kind() == Kind.INCOMING) {
            // source tab reappears with all its content (pages recalc on screen reopen)
            msg = Component.translatable("itemsorganisator.toast.incoming_cleared",
                    tabNameOrId(entry.targetTab()));
        } else if (entry.kind() == Kind.TAB && mode == Mode.ITEM && wasHidden) {
            // rescue flow: item pulled OUT of Hidden into a chosen tab
            CreativeModeTab target = BuiltInRegistries.CREATIVE_MODE_TAB.get(entry.targetTab());
            Component name = target != null ? target.getDisplayName()
                    : Component.literal(String.valueOf(entry.targetTab()));
            msg = Component.translatable("itemsorganisator.toast.restored", name);
        } else if (entry.kind() == Kind.HIDDEN && mode == Mode.TAB) {
            msg = Component.translatable("itemsorganisator.toast.tab_hidden", snapshotCount);
        } else {
            msg = Component.translatable(switch (entry.kind()) {
                case BLACKLIST -> "itemsorganisator.toast.blacklisted";
                case CANCEL -> "itemsorganisator.toast.cleared";
                default -> "itemsorganisator.toast.saved";
            });
        }
        ClientHooks.toast(title, msg);
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
