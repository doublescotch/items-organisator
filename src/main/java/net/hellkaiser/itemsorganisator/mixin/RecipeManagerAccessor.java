package net.hellkaiser.itemsorganisator.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {
    @Accessor("recipes")
    Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> itemsorganisator$getRecipes();

    @Accessor("recipes")
    void itemsorganisator$setRecipes(Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes);

    @Accessor("byName")
    Map<ResourceLocation, Recipe<?>> itemsorganisator$getByName();

    @Accessor("byName")
    void itemsorganisator$setByName(Map<ResourceLocation, Recipe<?>> byName);
}
