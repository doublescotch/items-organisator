package net.hellkaiser.itemsorganisator.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * config/itemsorganisator.json — the single source of truth.
 * <pre>
 * { "tabMoves":    { "sourcemod:source_tab_id": "target_tab_id" },
 *   "itemMoves":   { "somemod:item_id": "target_tab_id" },
 *   "hidden":      [ "somemod:item_id" ],
 *   "blacklisted": [ "somemod:item_id", "!minecraft:.*_sword" ] }
 * </pre>
 * Two removal tiers:
 * - hidden: removed from the pack, but creative admins (permission >= 2) keep it in the
 *   Hidden tab and their own inventory; they can use it, yet can never place it, drop it
 *   into the world, or park it in a container.
 * - blacklisted: TOTAL eradication for everyone, creative admins included. Entries are
 *   plain ids or "!"-prefixed Java regexes over the full item id.
 *
 * Missing references (mod removed from the pack) are skipped silently.
 * Parse errors: one warning, file backed up to .broken once, empty config used.
 */
public final class OrganisatorConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final String MODID = "itemsorganisator";
    public static final ResourceLocation HIDDEN_TAB_ID = new ResourceLocation(MODID, "hidden");

    private static final String NOTE =
            "hidden = retire du pack, mais les admins creatifs (permission >= 2) le gardent dans l'onglet Hidden et leur inventaire "
            + "(usage ok, placement/drop/coffres interdits) / removed from the pack, creative admins keep it in the Hidden tab and "
            + "their inventory (usable, but placing/dropping/chests are blocked). "
            + "Masquer un onglet entier: geste en jeu (clic droit sur l'en-tete) = snapshot en entrees hidden individuelles; "
            + "tabMoves vers l'onglet Hidden n'est PAS supporte / Hide a whole tab: in-game gesture (right-click its header) "
            + "snapshots it into individual hidden entries; tabMoves targeting the Hidden tab is NOT supported. "
            + "blacklisted = eradication TOTALE pour tout le monde, admins creatifs compris; ids simples ou regex prefixees par '!' "
            + "(ex: \"!minecraft:.*_sword\") / TOTAL eradication for everyone including creative admins; plain ids or '!'-prefixed regexes. "
            + "removeRecipesUsingHidden = retirer AUSSI les recettes qui consomment un objet restreint (evite les recettes visibles mais irrealisables) "
            + "/ also remove recipes that CONSUME a restricted item (prevents visible-but-uncraftable recipes).";

    private static volatile OrganisatorConfig instance;
    private static long lastCheckMs = 0L;
    private static long lastMtime = -1L;
    private static final Object LOCK = new Object();

    // ---- raw data (sorted for stable, diff-friendly saves) ----
    private final TreeMap<String, String> tabMoves = new TreeMap<>();
    private final TreeMap<String, String> itemMoves = new TreeMap<>();
    private final TreeSet<String> hidden = new TreeSet<>();
    private final TreeSet<String> blacklisted = new TreeSet<>();

    /**
     * Si vrai, retire AUSSI les recettes qui CONSOMMENT un objet restreint (au lieu des
     * seules recettes qui le produisent). Evite les "branches mortes": une recette
     * parfaitement visible dans JEI/EMI mais irrealisable faute d'ingredient obtenable.
     * Defaut false: certains packs fournissent l'ingredient par loot ou commande admin.
     */
    private boolean removeRecipesUsingHidden = false;

    /**
     * Propagation en chaine: un objet dont TOUTES les recettes ont ete retirees devient
     * lui-meme "mort", et les recettes qui le consomment tombent a leur tour (point fixe).
     * Necessite removeRecipesUsingHidden. Defaut false: "plus aucune recette" ne veut pas
     * dire "inobtenable" (butin, mobs, minerais, echanges).
     */
    private boolean cascadeRecipeRemoval = false;

    /**
     * Garde-fou: si la cascade devait retirer plus de ce pourcentage de TOUTES les recettes
     * du jeu, elle est ABANDONNEE (seul le retrait direct s'applique) et une erreur est
     * ecrite dans le log. Protege d'une regle malencontreuse (masquer les planches...)
     * qui viderait le jeu en silence sur un serveur. 0 = pas de garde-fou.
     */
    private int cascadeAbortPercent = 33;

    /**
     * GARDE-FOU PRINCIPAL: objets intouchables (ni hidden, ni blacklist).
     * Masquer un bloc de terrain naturel (terre, pierre, bois, sable...) rend le jeu
     * ingerable: le monde en est fait, chaque bloc mine produirait un objet aussitot
     * detruit. On protege donc par TAGS (item ET bloc) + ids explicites.
     * Une entree protegee presente dans hidden/blacklisted est ignoree avec un warning.
     */
    private final TreeSet<String> protectedTags = new TreeSet<>(List.of(
            "minecraft:dirt",
            "minecraft:sand",
            "minecraft:logs",
            "minecraft:planks",
            "minecraft:leaves",
            "minecraft:base_stone_overworld",
            "minecraft:base_stone_nether",
            "minecraft:stone_ore_replaceables",
            "minecraft:deepslate_ore_replaceables"
    ));
    private final TreeSet<String> protectedItems = new TreeSet<>(List.of(
            "minecraft:gravel",
            "minecraft:water_bucket",
            "minecraft:crafting_table"
    ));
    /** Passe-droit assume: l'auteur du pack sait ce qu'il fait. */
    private boolean allowProtectedOverride = false;

    // ---- resolved views (rebuilt on load/edit; unresolvable entries silently dropped) ----
    private final Map<ResourceLocation, ResourceLocation> tabMovesRes = new LinkedHashMap<>();
    private final Map<ResourceLocation, ResourceLocation> itemMovesRes = new LinkedHashMap<>();
    private final Set<Item> hiddenItems = new LinkedHashSet<>();      // hidden tier only (blacklist excluded)
    private final Set<Item> blacklistItems = new LinkedHashSet<>();   // blacklist tier (ids + regex matches)
    private final Set<Item> restrictedItems = new LinkedHashSet<>();  // union, for recipes/JEI

    private OrganisatorConfig() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(MODID + ".json");
    }

    /** Hot-reloadable access: re-stats the file at most every 2 s and reloads on change. */
    public static OrganisatorConfig get() {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            if (instance == null || now - lastCheckMs > 2000L) {
                lastCheckMs = now;
                long mtime = -1L;
                try {
                    Path f = file();
                    if (Files.exists(f)) mtime = Files.getLastModifiedTime(f).toMillis();
                } catch (IOException ignored) {}
                if (instance == null || mtime != lastMtime) {
                    lastMtime = mtime;
                    instance = load();
                }
            }
            return instance;
        }
    }

    private static OrganisatorConfig load() {
        OrganisatorConfig cfg = new OrganisatorConfig();
        Path f = file();
        if (!Files.exists(f)) {
            cfg.saveNow(); // write empty skeleton so pack authors find the file
            cfg.resolve();
            return cfg;
        }
        try {
            String text = Files.readString(f, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            readMap(root, "tabMoves", cfg.tabMoves);
            readMap(root, "itemMoves", cfg.itemMoves);
            readList(root, "hidden", cfg.hidden);
            readList(root, "blacklisted", cfg.blacklisted);
            if (root.has("removeRecipesUsingHidden") && root.get("removeRecipesUsingHidden").isJsonPrimitive()) {
                cfg.removeRecipesUsingHidden = root.get("removeRecipesUsingHidden").getAsBoolean();
            }
            if (root.has("cascadeRecipeRemoval") && root.get("cascadeRecipeRemoval").isJsonPrimitive()) {
                cfg.cascadeRecipeRemoval = root.get("cascadeRecipeRemoval").getAsBoolean();
            }
            if (root.has("cascadeAbortPercent") && root.get("cascadeAbortPercent").isJsonPrimitive()) {
                cfg.cascadeAbortPercent = Math.max(0, Math.min(100, root.get("cascadeAbortPercent").getAsInt()));
            }
            if (root.has("allowProtectedOverride") && root.get("allowProtectedOverride").isJsonPrimitive()) {
                cfg.allowProtectedOverride = root.get("allowProtectedOverride").getAsBoolean();
            }
            if (root.has("protectedTags") && root.get("protectedTags").isJsonArray()) {
                cfg.protectedTags.clear();
                readList(root, "protectedTags", cfg.protectedTags);
            }
            if (root.has("protectedItems") && root.get("protectedItems").isJsonArray()) {
                cfg.protectedItems.clear();
                readList(root, "protectedItems", cfg.protectedItems);
            }
        } catch (Exception ex) {
            LOGGER.warn("[Items Organisator] config {} is invalid, using an empty config ({})", f, ex.toString());
            try {
                Path broken = f.resolveSibling(f.getFileName() + ".broken");
                if (!Files.exists(broken)) Files.copy(f, broken, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {}
            cfg = new OrganisatorConfig();
        }
        cfg.resolve();
        return cfg;
    }

    private static void readMap(JsonObject root, String key, Map<String, String> into) {
        if (root.has(key) && root.get(key).isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject(key).entrySet()) {
                if (e.getValue().isJsonPrimitive()) into.put(e.getKey(), e.getValue().getAsString());
            }
        }
    }

    private static void readList(JsonObject root, String key, Set<String> into) {
        if (root.has(key) && root.get(key).isJsonArray()) {
            for (JsonElement e : root.getAsJsonArray(key)) {
                if (e.isJsonPrimitive()) into.add(e.getAsString());
            }
        }
    }

    private void resolve() {
        tabMovesRes.clear();
        itemMovesRes.clear();
        hiddenItems.clear();
        blacklistItems.clear();
        restrictedItems.clear();

        boolean warnedHiddenTarget = false;
        for (Map.Entry<String, String> e : tabMoves.entrySet()) {
            ResourceLocation src = ResourceLocation.tryParse(e.getKey());
            ResourceLocation dst = ResourceLocation.tryParse(e.getValue());
            if (src == null || dst == null || src.equals(dst)) continue;
            if (HIDDEN_TAB_ID.equals(dst)) {
                // cosmetic-only footgun: relocation to Hidden would NOT enforce anything.
                if (!warnedHiddenTarget) {
                    LOGGER.warn("[Items Organisator] tabMoves vers l'onglet Hidden non supporte ('{}' ignore) — "
                            + "utilisez le geste de masquage en jeu (snapshot en entrees hidden)", e.getKey());
                    warnedHiddenTarget = true;
                }
                continue;
            }
            tabMovesRes.put(src, dst);
        }
        for (Map.Entry<String, String> e : itemMoves.entrySet()) {
            ResourceLocation item = ResourceLocation.tryParse(e.getKey());
            ResourceLocation dst = ResourceLocation.tryParse(e.getValue());
            if (item != null && dst != null) itemMovesRes.put(item, dst);
        }

        // blacklist tier: plain ids + "!" regexes over the whole item id (IO-compatible syntax)
        List<Pattern> patterns = new ArrayList<>();
        for (String s : blacklisted) {
            if (s == null || s.isBlank() || s.startsWith("//")) continue; // "//" = comment entry, IO-style
            if (s.startsWith("!")) {
                try {
                    patterns.add(Pattern.compile(s.substring(1)));
                } catch (PatternSyntaxException ex) {
                    LOGGER.debug("[Items Organisator] invalid blacklist regex '{}' skipped", s);
                }
            } else {
                ResourceLocation id = ResourceLocation.tryParse(s);
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                    blacklistItems.add(BuiltInRegistries.ITEM.get(id));
                }
            }
        }
        if (!patterns.isEmpty()) {
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
                String idStr = id.toString();
                if (idStr.equals("minecraft:air")) continue;
                for (Pattern p : patterns) {
                    if (p.matcher(idStr).matches()) {
                        blacklistItems.add(BuiltInRegistries.ITEM.get(id));
                        break;
                    }
                }
            }
        }

        // hidden tier (blacklist wins: a blacklisted item is not spawnable, not even via the Hidden tab)
        for (String s : hidden) {
            ResourceLocation id = ResourceLocation.tryParse(s);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            Item item = BuiltInRegistries.ITEM.get(id);
            if (!blacklistItems.contains(item)) hiddenItems.add(item);
        }

        // GARDE-FOU: les blocs/objets proteges ne peuvent jamais etre restreints.
        if (!allowProtectedOverride) {
            List<String> refused = new ArrayList<>();
            for (Item item : new ArrayList<>(blacklistItems)) {
                if (isProtected(item)) {
                    blacklistItems.remove(item);
                    refused.add(String.valueOf(BuiltInRegistries.ITEM.getKey(item)));
                }
            }
            for (Item item : new ArrayList<>(hiddenItems)) {
                if (isProtected(item)) {
                    hiddenItems.remove(item);
                    refused.add(String.valueOf(BuiltInRegistries.ITEM.getKey(item)));
                }
            }
            if (!refused.isEmpty()) {
                LOGGER.warn("[Items Organisator] {} protected item(s) ignored (natural terrain / essentials): {}"
                                + " — set allowProtectedOverride=true if this is really intended.",
                        refused.size(), String.join(", ", refused));
            }
        }

        restrictedItems.addAll(blacklistItems);
        restrictedItems.addAll(hiddenItems);
    }

    /**
     * Objet protege: bloc de terrain naturel ou essentiel. Verifie les tags d'OBJET et,
     * pour un BlockItem, les tags de BLOC (base_stone_overworld n'existe qu'en tag de bloc).
     */
    public boolean isProtected(Item item) {
        if (item == null) return false;
        if (allowProtectedOverride) return false;
        try {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null && protectedItems.contains(id.toString())) return true;

            for (String raw : protectedTags) {
                ResourceLocation tagId = ResourceLocation.tryParse(raw);
                if (tagId == null) continue;
                if (item.builtInRegistryHolder().is(
                        net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, tagId))) {
                    return true;
                }
                if (item instanceof net.minecraft.world.item.BlockItem bi
                        && bi.getBlock().builtInRegistryHolder().is(
                                net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, tagId))) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return false; // tags pas encore charges: on ne bloque pas
        }
    }

    // ------------------------------------------------------------------ queries

    /** Hidden tier only (admin-spawnable). Blacklisted items are excluded. */
    public Set<Item> hiddenItems() {
        return Collections.unmodifiableSet(hiddenItems);
    }

    /** Blacklist tier (total eradication, no exemption). */
    public Set<Item> blacklistedItems() {
        return Collections.unmodifiableSet(blacklistItems);
    }

    /** Union of both tiers — for recipe removal, JEI hiding, tab insertion skips. */
    /** Retirer aussi les recettes qui CONSOMMENT un objet restreint (defaut false). */
    public boolean removeRecipesUsingHidden() {
        return removeRecipesUsingHidden;
    }

    /** Propager en chaine jusqu'au point fixe (necessite removeRecipesUsingHidden). */
    public boolean cascadeRecipeRemoval() {
        return cascadeRecipeRemoval;
    }

    /** Seuil d'abandon de la cascade, en % de toutes les recettes (0 = desactive). */
    public int cascadeAbortPercent() {
        return cascadeAbortPercent;
    }

    public Set<Item> restrictedItems() {
        return Collections.unmodifiableSet(restrictedItems);
    }

    /** Raw hidden id strings, as written in the JSON (unresolved entries included). */
    public Set<String> rawHidden() {
        return Collections.unmodifiableSet(hidden);
    }

    public boolean isHidden(Item item) {
        return hiddenItems.contains(item);
    }

    public boolean isBlacklisted(Item item) {
        return blacklistItems.contains(item);
    }

    public boolean isRestricted(Item item) {
        return restrictedItems.contains(item);
    }

    private static boolean tabExists(ResourceLocation tabId) {
        return BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(tabId);
    }

    /** Active target tab for an item move, or null (no rule, or target tab absent from the pack). */
    public ResourceLocation activeItemMoveTarget(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) return null;
        ResourceLocation t = itemMovesRes.get(id);
        return (t != null && tabExists(t)) ? t : null;
    }

    /** Active target tab for a whole-tab move, or null. */
    public ResourceLocation activeTabMoveTarget(ResourceLocation tabId) {
        ResourceLocation t = tabMovesRes.get(tabId);
        return (t != null && !t.equals(tabId) && tabExists(t)) ? t : null;
    }

    public Map<ResourceLocation, ResourceLocation> itemMovesResolved() {
        return Collections.unmodifiableMap(itemMovesRes);
    }

    public Map<ResourceLocation, ResourceLocation> tabMovesResolved() {
        return Collections.unmodifiableMap(tabMovesRes);
    }

    // ------------------------------------------------------------------ edits (used by the in-game UI)

    /** Hidden tier. @return true if the restricted (hidden/blacklist) sets changed. */
    public boolean hide(ResourceLocation itemId) {
        String key = itemId.toString();
        boolean changed = hidden.add(key);
        changed |= blacklisted.remove(key); // demote from blacklist if it was a plain entry
        itemMoves.remove(key);
        resolve();
        return changed;
    }

    /**
     * Bulk-hide (whole-tab snapshot). @return true if the restricted sets changed.
     * Explicit snapshot semantics: only the ids passed now are hidden.
     */
    public boolean hideAll(java.util.Collection<ResourceLocation> itemIds) {
        boolean changed = false;
        for (ResourceLocation id : itemIds) {
            String key = id.toString();
            changed |= hidden.add(key);
            itemMoves.remove(key);
        }
        resolve();
        return changed;
    }

    /**
     * Group restore (Hidden management screen): delete these raw hidden entries only —
     * itemMoves/blacklist untouched. @return true if anything was removed.
     */
    public boolean unhideAll(java.util.Collection<String> rawEntries) {
        boolean changed = hidden.removeAll(rawEntries);
        resolve();
        return changed;
    }

    /** Blacklist tier (plain id entry). @return true if the restricted sets changed. */
    public boolean blacklist(ResourceLocation itemId) {
        String key = itemId.toString();
        boolean changed = blacklisted.add(key);
        changed |= hidden.remove(key);
        itemMoves.remove(key);
        resolve();
        return changed;
    }

    /** @return true if the restricted sets changed (item was un-hidden/un-blacklisted). */
    public boolean setItemMove(ResourceLocation itemId, ResourceLocation targetTab) {
        String key = itemId.toString();
        boolean changed = hidden.remove(key);
        changed |= blacklisted.remove(key);
        itemMoves.put(key, targetTab.toString());
        resolve();
        return changed;
    }

    /** @return true if the restricted sets changed. Regex blacklist entries are not touched. */
    public boolean clearItemRules(ResourceLocation itemId) {
        String key = itemId.toString();
        boolean changed = hidden.remove(key);
        changed |= blacklisted.remove(key);
        itemMoves.remove(key);
        resolve();
        return changed;
    }

    public void setTabMove(ResourceLocation sourceTab, ResourceLocation targetTab) {
        tabMoves.put(sourceTab.toString(), targetTab.toString());
        resolve();
    }

    public void clearTabMove(ResourceLocation sourceTab) {
        tabMoves.remove(sourceTab.toString());
        resolve();
    }

    // ------------------------------------------------------------------ save

    /** Atomic, pretty-printed, sorted-keys save. */
    public void saveNow() {
        JsonObject root = new JsonObject();
        root.addProperty("_note", NOTE);
        JsonObject tm = new JsonObject();
        for (Map.Entry<String, String> e : tabMoves.entrySet()) tm.addProperty(e.getKey(), e.getValue());
        JsonObject im = new JsonObject();
        for (Map.Entry<String, String> e : itemMoves.entrySet()) im.addProperty(e.getKey(), e.getValue());
        JsonArray hid = new JsonArray();
        for (String s : hidden) hid.add(s);
        JsonArray black = new JsonArray();
        for (String s : blacklisted) black.add(s);
        root.add("tabMoves", tm);
        root.add("itemMoves", im);
        root.add("hidden", hid);
        root.add("blacklisted", black);
        root.addProperty("removeRecipesUsingHidden", removeRecipesUsingHidden);
        root.addProperty("cascadeRecipeRemoval", cascadeRecipeRemoval);
        root.addProperty("cascadeAbortPercent", cascadeAbortPercent);
        root.addProperty("allowProtectedOverride", allowProtectedOverride);
        JsonArray pTags = new JsonArray();
        for (String s : protectedTags) pTags.add(s);
        root.add("protectedTags", pTags);
        JsonArray pItems = new JsonArray();
        for (String s : protectedItems) pItems.add(s);
        root.add("protectedItems", pItems);

        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFail) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
            synchronized (LOCK) {
                try {
                    lastMtime = Files.getLastModifiedTime(f).toMillis();
                } catch (IOException ignored) {}
                lastCheckMs = System.currentTimeMillis();
                instance = this;
            }
        } catch (IOException ex) {
            LOGGER.warn("[Items Organisator] could not save {}: {}", f, ex.toString());
        }
    }
}
