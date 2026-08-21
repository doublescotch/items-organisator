package net.hellkaiser.itemsorganisator.recipe;

import com.mojang.logging.LogUtils;
import net.hellkaiser.itemsorganisator.config.OrganisatorConfig;
import net.hellkaiser.itemsorganisator.mixin.RecipeManagerAccessor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Filtrage des recettes selon les regles.
 *
 * Trois niveaux, du plus sur au plus agressif :
 * 1. TOUJOURS — retirer les recettes qui PRODUISENT un objet restreint.
 * 2. removeRecipesUsingHidden — retirer aussi celles qui le CONSOMMENT (sinon elles
 *    restent visibles dans JEI/EMI tout en etant irrealisables: "branches mortes").
 * 3. cascadeRecipeRemoval — propager: un objet dont TOUTES les recettes viennent d'etre
 *    retirees (et qui en avait au moins une) est considere orphelin, donc les recettes
 *    qui le consomment tombent a leur tour, jusqu'a point fixe.
 *
 * ATTENTION sur (3): "plus aucune recette" ne veut pas dire "inobtenable" — un objet
 * peut venir d'un butin, d'un mob, d'un minerai ou d'un echange. La cascade est donc
 * OPTIONNELLE, et un rapport est ecrit dans le log dans tous les cas pour que l'auteur
 * du pack voie exactement ce que ses regles impliquent.
 */
public final class RecipeFilter {
    private RecipeFilter() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_PASSES = 20;

    public static void filter(RecipeManager manager) {
        OrganisatorConfig cfg = OrganisatorConfig.get();
        Set<Item> restricted = cfg.restrictedItems(); // les deux niveaux perdent leurs recettes
        if (restricted.isEmpty()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        RegistryAccess access = server != null ? server.registryAccess() : RegistryAccess.EMPTY;

        boolean alsoIngredients = cfg.removeRecipesUsingHidden();
        boolean cascade = alsoIngredients && cfg.cascadeRecipeRemoval();

        RecipeManagerAccessor acc = (RecipeManagerAccessor) manager;
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> allRecipes = acc.itemsorganisator$getRecipes();

        // Index: pour chaque objet, le nombre de recettes qui le produisent (avant filtrage).
        Map<Item, Integer> producedBy = new HashMap<>();
        List<Recipe<?>> flat = new ArrayList<>();
        for (Map<ResourceLocation, Recipe<?>> byType : allRecipes.values()) {
            for (Recipe<?> r : byType.values()) {
                flat.add(r);
                Item out = resultItem(r, access);
                if (out != null) producedBy.merge(out, 1, Integer::sum);
            }
        }

        Set<Item> dead = new LinkedHashSet<>(restricted); // objets consideres non-obtenables
        Set<ResourceLocation> removed = new HashSet<>();
        Set<Item> orphaned = new LinkedHashSet<>();       // pour le rapport

        for (int pass = 0; pass < MAX_PASSES; pass++) {
            Map<Item, Integer> lostThisPass = new HashMap<>();
            boolean changed = false;

            for (Recipe<?> r : flat) {
                ResourceLocation id = r.getId();
                if (removed.contains(id)) continue;
                Item out = resultItem(r, access);
                boolean drop = (out != null && dead.contains(out))
                        || (alsoIngredients && usesDeadIngredient(r, dead));
                if (drop) {
                    removed.add(id);
                    changed = true;
                    if (out != null) lostThisPass.merge(out, 1, Integer::sum);
                }
            }
            if (!changed || !cascade) break;

            // Objets ayant perdu TOUTES leurs recettes -> orphelins -> nouvelle passe.
            boolean grew = false;
            for (Map.Entry<Item, Integer> e : lostThisPass.entrySet()) {
                Item item = e.getKey();
                if (dead.contains(item)) continue;
                int total = producedBy.getOrDefault(item, 0);
                if (total > 0 && countRemaining(flat, item, access, removed) == 0) {
                    dead.add(item);
                    orphaned.add(item);
                    grew = true;
                }
            }
            if (!grew) break;
        }

        if (removed.isEmpty()) return;

        // GARDE-FOU: une cascade qui emporte une part demesuree du jeu est presque
        // toujours une regle malencontreuse (masquer les planches, un tag entier...).
        // On l'abandonne et on retombe sur le retrait direct, en le disant fort.
        int abortPercent = cfg.cascadeAbortPercent();
        if (cascade && abortPercent > 0 && !flat.isEmpty()
                && removed.size() * 100 / flat.size() >= abortPercent) {
            LOGGER.error("[Items Organisator] CASCADE ABORTED: it would have removed {} of {} recipes ({}%,"
                            + " limit {}%). Falling back to direct removal only. Review your hidden/blacklisted"
                            + " rules — a foundational item is probably restricted.",
                    removed.size(), flat.size(), removed.size() * 100 / flat.size(), abortPercent);
            removed.clear();
            dead.clear();
            dead.addAll(restricted);
            orphaned.clear();
            for (Recipe<?> r : flat) {
                Item out = resultItem(r, access);
                if ((out != null && dead.contains(out))
                        || (alsoIngredients && usesDeadIngredient(r, dead))) {
                    removed.add(r.getId());
                }
            }
            if (removed.isEmpty()) return;
        }

        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> newRecipes = new HashMap<>();
        for (Map.Entry<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> byType : allRecipes.entrySet()) {
            Map<ResourceLocation, Recipe<?>> kept = new HashMap<>();
            for (Map.Entry<ResourceLocation, Recipe<?>> e : byType.getValue().entrySet()) {
                if (!removed.contains(e.getKey())) kept.put(e.getKey(), e.getValue());
            }
            newRecipes.put(byType.getKey(), kept);
        }
        Map<ResourceLocation, Recipe<?>> newByName = new HashMap<>();
        for (Map.Entry<ResourceLocation, Recipe<?>> e : acc.itemsorganisator$getByName().entrySet()) {
            if (!removed.contains(e.getKey())) newByName.put(e.getKey(), e.getValue());
        }
        acc.itemsorganisator$setRecipes(newRecipes);
        acc.itemsorganisator$setByName(newByName);

        report(removed.size(), orphaned, flat, access, removed, cascade);
    }

    /** Rapport d'audit: ce que les regles impliquent reellement pour la progression. */
    private static void report(int removedCount, Set<Item> orphaned, List<Recipe<?>> flat,
                               RegistryAccess access, Set<ResourceLocation> removed, boolean cascade) {
        LOGGER.info("[Items Organisator] {} recipe(s) removed by the current rules.", removedCount);

        // Objets ayant perdu toutes leurs recettes, meme sans cascade active: c'est
        // l'information que l'auteur du pack doit voir pour decider.
        Set<Item> lostAll = new LinkedHashSet<>(orphaned);
        if (!cascade) {
            Map<Item, Integer> before = new HashMap<>();
            for (Recipe<?> r : flat) {
                Item out = resultItem(r, access);
                if (out != null) before.merge(out, 1, Integer::sum);
            }
            for (Map.Entry<Item, Integer> e : before.entrySet()) {
                if (countRemaining(flat, e.getKey(), access, removed) == 0) lostAll.add(e.getKey());
            }
        }
        if (lostAll.isEmpty()) return;

        List<String> names = new ArrayList<>();
        for (Item item : lostAll) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null) names.add(id.toString());
            if (names.size() >= 25) break;
        }
        LOGGER.info("[Items Organisator] {} item(s) no longer have any recipe (they may still drop from"
                        + " loot, mobs or ores — review if they gate progression): {}{}",
                lostAll.size(), String.join(", ", names),
                lostAll.size() > names.size() ? ", …" : "");
    }

    private static int countRemaining(List<Recipe<?>> flat, Item item, RegistryAccess access,
                                      Set<ResourceLocation> removed) {
        int n = 0;
        for (Recipe<?> r : flat) {
            if (removed.contains(r.getId())) continue;
            if (item.equals(resultItem(r, access))) n++;
        }
        return n;
    }

    private static Item resultItem(Recipe<?> recipe, RegistryAccess access) {
        try {
            ItemStack result = recipe.getResultItem(access);
            return (result == null || result.isEmpty()) ? null : result.getItem();
        } catch (Throwable t) {
            return null; // recette exotique: ignoree, silencieusement
        }
    }

    /**
     * Un Ingredient peut accepter PLUSIEURS objets (tag, liste). On ne considere la
     * recette perdue que si TOUTES les alternatives sont mortes — sinon elle reste
     * realisable avec une autre entree du tag et la retirer serait un faux positif.
     */
    private static boolean usesDeadIngredient(Recipe<?> recipe, Set<Item> dead) {
        try {
            for (Ingredient ing : recipe.getIngredients()) {
                if (ing == null || ing.isEmpty()) continue;
                ItemStack[] options = ing.getItems();
                if (options == null || options.length == 0) continue;
                boolean allDead = true;
                for (ItemStack option : options) {
                    if (option == null || option.isEmpty()) continue;
                    if (!dead.contains(option.getItem())) {
                        allDead = false;
                        break;
                    }
                }
                if (allDead) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}
