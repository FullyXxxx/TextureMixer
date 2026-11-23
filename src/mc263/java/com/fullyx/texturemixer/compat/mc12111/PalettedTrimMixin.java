package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.resources.palette.PalettedTextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer$TrimTextureKey")
public abstract class PalettedTrimMixin {
    @WrapOperation(method = "getOrPrepareTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/palette/PalettedTextureManager;getOrPrepare(Lnet/minecraft/resources/Identifier;Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/resources/palette/PalettedTextureManager$Handle;"))
    private PalettedTextureManager.Handle texturemixer$trim(PalettedTextureManager manager, Identifier base,
                                                            Identifier palette, Operation<PalettedTextureManager.Handle> original) {
        return original.call(manager, texturemixer$selected(base, "textures/"), texturemixer$selected(palette, "textures/palettes/"));
    }

    @ModifyVariable(method = "createTextureWithNoPalette", at = @At("HEAD"), argsOnly = true)
    private static Identifier texturemixer$directTrim(Identifier base) {
        return texturemixer$selected(base, "textures/");
    }

    @Unique
    private static Identifier texturemixer$selected(Identifier id, String directory) {
        var path = id.withPath(directory + id.getPath() + ".png");
        var replacement = RenderScope.texture(path);
        return path.equals(replacement) ? id : Identifier.fromNamespaceAndPath(replacement.getNamespace(), id.getPath());
    }
}
