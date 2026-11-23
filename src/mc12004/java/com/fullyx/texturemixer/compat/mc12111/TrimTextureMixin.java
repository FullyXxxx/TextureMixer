package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.item.equipment.EquipmentModel.LayerType;
import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ArmorTrim.class)
public abstract class TrimTextureMixin {
    @WrapMethod(method="getTexture")
    private Identifier texturemixer$trim(LayerType layer,Identifier asset,Operation<Identifier> original){
        String prefix=RenderScope.prefix(asset.getNamespace());
        Identifier texture=original.call(layer,RenderScope.original(asset));
        return prefix==null?texture:new Identifier(prefix+texture.getNamespace(),texture.getPath());
    }
}
