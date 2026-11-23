package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.RenderScope;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import com.fullyx.texturemixer.client.EnchantPreview;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(TextureManager.class)
public abstract class TextureBindingMixin {
    @ModifyVariable(method="getTexture",at=@At("HEAD"),argsOnly=true)
    private Identifier texturemixer$texture(Identifier id){return RenderScope.texture(EnchantPreview.itemTexture(id));}
}
