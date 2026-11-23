package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.core.AssetId;
import com.fullyx.texturemixer.core.GraphCompiler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

final class NativePackModels {
    private NativePackModels(){}

    static AssetId equipment(ItemStack stack){
        return wornTextures(stack).isEmpty()?null:AssetId.of(Registries.ITEM.getId(stack.getItem()).toString());
    }

    static GraphCompiler.Result compile(GraphCompiler compiler,Catalog.Target target)throws IOException{
        compiler.legacyItem(target.model(),null,false);
        return compiler.legacyDirectWear(wornTextures(target.stack()),target.equipment()!=null);
    }

    static GraphCompiler.Result previewWear(GraphCompiler compiler,Catalog.Target target)throws IOException{
        return compiler.legacyDirectWear(wornTextures(target.stack()),false);
    }

    static GraphCompiler.Result previewItem(GraphCompiler compiler,Catalog.Target target,boolean worn)throws IOException{
        var item=compiler.legacyItem(target.model(),null,true);
        return worn?compiler.legacyDirectWear(wornTextures(target.stack()),true):item;
    }

    static List<AssetId> wornTextures(ItemStack stack){
        var textures=new ArrayList<AssetId>();
        if(stack.getItem() instanceof net.minecraft.item.HorseArmorItem armor){
            textures.add(AssetId.of(armor.getEntityTexture().toString()));
        }else if(stack.getItem() instanceof ArmorItem armor){
            String material=armor.getMaterial().getName();
            AssetId name=AssetId.of(material);
            String layer=armor.getSlotType()==EquipmentSlot.LEGS?"2":"1";
            var texture=new AssetId(name.namespace(),"textures/models/armor/"+name.path()+"_layer_"+layer+".png");
            textures.add(texture);
            if(material.equals("leather"))textures.add(texture.withPath(texture.path().replace(".png","_overlay.png")));
        }else if(stack.isOf(Items.ELYTRA)){
            textures.add(AssetId.of("textures/entity/elytra.png"));
        }
        return List.copyOf(textures);
    }
}
