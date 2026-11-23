package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.TextureMixerClient;
import net.minecraft.resource.*;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResourcePackManager.class)
public abstract class PackManagerMixin {
    @Inject(method="createResourcePacks",at=@At("RETURN"),cancellable=true)
    private void texturemixer$packs(CallbackInfoReturnable<List<ResourcePack>> callback){
        var service=TextureMixerClient.SERVICE;
        var client=net.minecraft.client.MinecraftClient.getInstance();
        if(client!=null&&client.getResourcePackManager()==(Object)this){
            var packs=service==null?callback.getReturnValue():service.resourcePacks(callback.getReturnValue());
            var result=new java.util.ArrayList<>(packs);result.add(com.fullyx.texturemixer.client.UiFonts.pack(client.getDefaultResourcePack()));
            callback.setReturnValue(result);
        }
    }
}
