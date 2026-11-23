package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.EquipmentBindings;
import net.minecraft.client.render.entity.equipment.*;
import net.minecraft.item.equipment.EquipmentModel;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EquipmentModelLoader.class)
public abstract class EquipmentLoaderMixin {
    @Inject(method="get",at=@At("HEAD"),cancellable=true)
    private void texturemixer$preview(Identifier key,CallbackInfoReturnable<EquipmentModel> callback){
        EquipmentModel model=EquipmentBindings.previewModel(key);if(model!=null)callback.setReturnValue(model);
    }
}
