package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.*;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(SpecialModelWrapper.Unbaked.class)
public abstract class SpecialItemMixin {
    @Shadow @Final private Identifier base;
    @Redirect(method="bake",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/special/SpecialModelRenderer$Unbaked;bake(Lnet/minecraft/client/renderer/special/SpecialModelRenderer$BakingContext;)Lnet/minecraft/client/renderer/special/SpecialModelRenderer;"))
    private SpecialModelRenderer<?> texturemixer$bake(SpecialModelRenderer.Unbaked unbaked,SpecialModelRenderer.BakingContext context){
        String prefix=RenderScope.prefix(base.getNamespace());if(prefix==null)return unbaked.bake(context);
        SpriteGetter holder=id->{
            Identifier texture=id.texture();if(!texture.getNamespace().startsWith("texturemixer_"))texture=Identifier.fromNamespaceAndPath(prefix+texture.getNamespace(),texture.getPath());
            return context.sprites().get(new SpriteId(id.atlasLocation(),texture));
        };
        String arena=RenderScope.currentArena();
        SpecialModelRenderer<?> renderer=unbaked.bake(new SpecialModelRenderer.BakingContext() {
            public net.minecraft.client.model.geom.EntityModelSet entityModelSet() { return context.entityModelSet(); }
            public SpriteGetter sprites() { return holder; }
            public net.minecraft.client.renderer.PlayerSkinRenderCache playerSkinRenderCache() { return context.playerSkinRenderCache(); }
        });
        return renderer==null?null:new ScopedSpecialRenderer<>(renderer,prefix,arena);
    }
}
