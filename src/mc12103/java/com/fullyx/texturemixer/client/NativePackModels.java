package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.core.AssetId;
import com.fullyx.texturemixer.core.GraphCompiler;
import java.io.IOException;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;

final class NativePackModels {
    private NativePackModels(){}

    static AssetId equipment(ItemStack stack){
        var equippable=stack.get(DataComponentTypes.EQUIPPABLE);
        return equippable==null?null:equippable.model().map(id->AssetId.of(id.toString())).orElse(null);
    }
    static boolean equippable(ItemStack stack){return stack.get(DataComponentTypes.EQUIPPABLE)!=null;}
    static AssetId model(ItemStack stack,net.minecraft.util.Identifier item){
        var model=stack.get(DataComponentTypes.ITEM_MODEL);
        return AssetId.of((model==null?item:model).toString());
    }

    static GraphCompiler.Result compile(GraphCompiler compiler,Catalog.Target target)throws IOException{
        return compiler.legacyItem(target.model(),target.equipment(),false);
    }
}
