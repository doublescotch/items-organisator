package net.hellkaiser.itemsorganisator.mixin;

import com.google.gson.JsonElement;
import net.hellkaiser.itemsorganisator.recipe.RecipeFilter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** After (data-pack) recipe loading, drop every recipe whose result is a hidden item. */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Inject(
        method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("TAIL")
    )
    private void itemsorganisator$stripHiddenRecipes(Map<ResourceLocation, JsonElement> jsons, ResourceManager rm, ProfilerFiller profiler, CallbackInfo ci) {
        try {
            RecipeFilter.filter((RecipeManager) (Object) this);
        } catch (Throwable ignored) {
            // never break recipe loading
        }
    }
}
