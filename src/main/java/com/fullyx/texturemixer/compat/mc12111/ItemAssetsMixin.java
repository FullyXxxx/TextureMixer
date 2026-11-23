package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.core.AssetId;
import net.minecraft.client.item.ItemAssetsLoader;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.HashMap;
import java.util.Map;

@Mixin(ItemAssetsLoader.class)
public abstract class ItemAssetsMixin {
    /** Real Arrow Tip aliases its own definitions to vanilla IDs after resource priority is resolved. */
    @Inject(method="method_65928",at=@At("RETURN"),cancellable=true)
    private static void texturemixer$selectedModels(ResourceManager resources, CallbackInfoReturnable<Map<Identifier,Resource>> callback) {
        var service=TextureMixerClient.SERVICE;
        var generation=service==null?null:service.runtime();
        if(generation==null)return;
        Map<Identifier,Resource> definitions=new HashMap<>(callback.getReturnValue());
        definitions.keySet().removeIf(id->id.getNamespace().equals("real_arrow_tip")
                &&generation.assets().contains(new AssetId("minecraft",id.getPath())));
        callback.setReturnValue(definitions);
    }
}
