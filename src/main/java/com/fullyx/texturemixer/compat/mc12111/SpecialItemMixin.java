package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.VersionedIdentifiers;
import com.fullyx.texturemixer.client.*;
import net.minecraft.client.render.item.model.SpecialItemModel;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.texture.*;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(SpecialItemModel.Unbaked.class)
public abstract class SpecialItemMixin {
    @Shadow @Final private Identifier base;
    @Redirect(method="bake",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/item/model/special/SpecialModelRenderer$Unbaked;bake(Lnet/minecraft/client/render/item/model/special/SpecialModelRenderer$BakeContext;)Lnet/minecraft/client/render/item/model/special/SpecialModelRenderer;"))
    private SpecialModelRenderer<?> texturemixer$bake(SpecialModelRenderer.Unbaked unbaked,SpecialModelRenderer.BakeContext context){
        String prefix=RenderScope.prefix(base.getNamespace());if(prefix==null)return unbaked.bake(context);
        SpriteHolder holder=id->{
            Identifier texture=id.getTextureId();if(!texture.getNamespace().startsWith("texturemixer_"))texture=VersionedIdentifiers.of(prefix+texture.getNamespace(),texture.getPath());
            return context.spriteHolder().getSprite(new SpriteIdentifier(id.getAtlasId(),texture));
        };
        String arena=RenderScope.currentArena();
        SpecialModelRenderer<?> renderer=unbaked.bake(new SpecialModelRenderer.BakeContext.Simple(context.entityModelSet(),holder,context.playerSkinRenderCache()));
        return renderer==null?null:new ScopedSpecialRenderer<>(renderer,prefix,arena);
    }
}
