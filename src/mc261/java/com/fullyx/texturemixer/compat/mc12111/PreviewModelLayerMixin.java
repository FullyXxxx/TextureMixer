package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.RenderScope;
import java.util.List;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CuboidItemModelWrapper.class)
public abstract class PreviewModelLayerMixin {
    @Inject(method = "validateAtlasUsage", at = @At("HEAD"), cancellable = true)
    private static void texturemixer$previewAtlas(List<BakedQuad> quads, CallbackInfo callback) {
        if (quads.isEmpty() || RenderScope.currentArena() == null) return;
        var atlas = quads.getFirst().materialInfo().sprite().atlasLocation();
        if (atlas.getNamespace().equals("texturemixer") && atlas.getPath().startsWith("preview/")
                && quads.stream().allMatch(quad -> quad.materialInfo().sprite().atlasLocation().equals(atlas))) {
            callback.cancel();
        }
    }
}
