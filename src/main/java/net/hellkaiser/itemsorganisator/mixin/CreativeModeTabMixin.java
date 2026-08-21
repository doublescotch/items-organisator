package net.hellkaiser.itemsorganisator.mixin;

import net.hellkaiser.itemsorganisator.rules.RuleEngine;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removals: there is no Forge API to remove entries from a creative tab in 1.20.1,
 * so filter the freshly built display collections at the end of buildContents.
 * No @Shadow anywhere (hard rule) — collections are reached through the accessor mixin.
 */
@Mixin(CreativeModeTab.class)
public abstract class CreativeModeTabMixin {
    @Inject(method = "buildContents(Lnet/minecraft/world/item/CreativeModeTab$ItemDisplayParameters;)V", at = @At("TAIL"))
    private void itemsorganisator$filterAfterBuild(CreativeModeTab.ItemDisplayParameters params, CallbackInfo ci) {
        try {
            RuleEngine.filterTab((CreativeModeTab) (Object) this);
        } catch (Throwable t) {
            // never break tab building — but never hide the failure either (a silent
            // swallow here masked live-rebuild diagnostics; see v1.3.2)
            org.slf4j.LoggerFactory.getLogger("itemsorganisator")
                    .warn("[diag] filterTab failed for a tab: {}", t.toString());
        }
    }
}
