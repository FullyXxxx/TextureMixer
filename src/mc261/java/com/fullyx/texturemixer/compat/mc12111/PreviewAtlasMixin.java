package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.RenderScope;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(TextureAtlas.class)
public abstract class PreviewAtlasMixin {
    @ModifyVariable(method="<init>",at=@At("HEAD"),argsOnly=true)
    private static Identifier texturemixer$privateAtlas(Identifier id){return RenderScope.atlas(id);}
}
