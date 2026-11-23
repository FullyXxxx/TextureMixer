package com.fullyx.texturemixer.compat.mc12111;
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
    @Redirect(method="bake",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/item/model/special/SpecialModelRenderer$Unbaked;bake(Lnet/minecraft/client/render/entity/model/LoadedEntityModels;)Lnet/minecraft/client/render/item/model/special/SpecialModelRenderer;"))
    private SpecialModelRenderer<?> texturemixer$bake(SpecialModelRenderer.Unbaked unbaked,net.minecraft.client.render.entity.model.LoadedEntityModels models){
        String prefix=RenderScope.prefix(base.getNamespace());
        SpecialModelRenderer<?> renderer=unbaked.bake(models);
        return renderer==null||prefix==null?renderer:new ScopedSpecialRenderer<>(renderer,prefix,RenderScope.currentArena());
    }
}
