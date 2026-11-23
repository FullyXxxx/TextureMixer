package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.core.AssetId;
import com.fullyx.texturemixer.core.GraphCompiler;
import java.io.IOException;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

final class NativePackModels {
    private NativePackModels(){}

    static AssetId equipment(ItemStack stack){
        var equippable=stack.get(DataComponents.EQUIPPABLE);
        return equippable==null?null:equippable.assetId().map(key->AssetId.of(key.identifier().toString())).orElse(null);
    }

    static GraphCompiler.Result compile(GraphCompiler compiler,Catalog.Target target)throws IOException{
        return compiler.item(target.model(),target.equipment());
    }
}
