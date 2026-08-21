package net.hellkaiser.itemsorganisator.mixin;

import net.hellkaiser.itemsorganisator.config.OrganisatorConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.common.CreativeModeTabRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * L'ecran creatif construit ses pages depuis CreativeModeTabRegistry.getSortedCreativeModeTabs()
 * (ordre d'enregistrement des mods -> notre onglet Hidden atterrissait au milieu).
 * On force Hidden en TOUTE DERNIERE position. Classe Forge non obfusquee: remap=false.
 */
@Mixin(value = CreativeModeTabRegistry.class, remap = false)
public abstract class CreativeModeTabRegistryMixin {

    @Inject(method = "getSortedCreativeModeTabs", at = @At("RETURN"), cancellable = true)
    private static void itemsorganisator$hiddenTabLast(CallbackInfoReturnable<List<CreativeModeTab>> cir) {
        List<CreativeModeTab> tabs = cir.getReturnValue();
        if (tabs == null || tabs.isEmpty()) return;
        if (!net.hellkaiser.itemsorganisator.config.ClientControlsConfig.hiddenTabLast()) return;
        CreativeModeTab hidden = BuiltInRegistries.CREATIVE_MODE_TAB.get(OrganisatorConfig.HIDDEN_TAB_ID);
        if (hidden == null) return;
        int idx = tabs.indexOf(hidden);
        if (idx < 0 || idx == tabs.size() - 1) return;
        List<CreativeModeTab> reordered = new ArrayList<>(tabs);
        reordered.remove(hidden);
        reordered.add(hidden);
        cir.setReturnValue(Collections.unmodifiableList(reordered));
    }
}
