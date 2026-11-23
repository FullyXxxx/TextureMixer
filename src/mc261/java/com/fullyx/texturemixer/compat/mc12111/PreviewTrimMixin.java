package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.EquipmentBindings;
import java.util.function.Function;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(EquipmentLayerRenderer.class)
public abstract class PreviewTrimMixin {
    @Redirect(method="renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
        at=@At(value="INVOKE",target="Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;",ordinal=1))
    private Object texturemixer$previewTrim(Function<Object,TextureAtlasSprite> getter,Object key){
        var access=(TrimKeyAccess)key;TextureAtlasSprite sprite=EquipmentBindings.previewTrim(access.texturemixer$asset(),access.texturemixer$texture());
        return sprite==null?getter.apply(key):sprite;
    }
}
