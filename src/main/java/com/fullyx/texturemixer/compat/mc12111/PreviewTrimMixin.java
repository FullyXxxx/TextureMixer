package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.EquipmentBindings;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.texture.Sprite;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(EquipmentRenderer.class)
public abstract class PreviewTrimMixin {
    @Redirect(method="render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
        at=@At(value="INVOKE",target="Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;",ordinal=1))
    private Object texturemixer$previewTrim(Function<Object,Sprite> getter,Object key){
        var access=(TrimKeyAccess)key;Sprite sprite=EquipmentBindings.previewTrim(access.texturemixer$asset(),access.texturemixer$texture());
        return sprite==null?getter.apply(key):sprite;
    }
}
