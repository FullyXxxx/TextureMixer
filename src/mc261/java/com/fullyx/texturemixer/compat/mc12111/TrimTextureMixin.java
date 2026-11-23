package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.RenderScope;
import net.minecraft.client.resources.model.EquipmentClientInfo.LayerType;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer$TrimSpriteKey")
public abstract class TrimTextureMixin {
    @Shadow @Final private ArmorTrim trim;
    @Shadow @Final private LayerType layerType;
    @Shadow @Final private ResourceKey<EquipmentAsset> equipmentAssetId;
    @Inject(method="spriteId",at=@At("HEAD"),cancellable=true)
    private void texturemixer$trim(CallbackInfoReturnable<Identifier> callback){
        String prefix=RenderScope.prefix(equipmentAssetId.identifier().getNamespace());if(prefix==null)return;
        var original=ResourceKey.create(EquipmentAssets.ROOT_ID,RenderScope.original(equipmentAssetId.identifier()));
        Identifier texture=trim.layerAssetId(layerType.trimAssetPrefix(),original);
        callback.setReturnValue(Identifier.fromNamespaceAndPath(prefix+texture.getNamespace(),texture.getPath()));
    }
}
