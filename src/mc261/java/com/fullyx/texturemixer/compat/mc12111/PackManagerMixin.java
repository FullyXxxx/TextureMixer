package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.TextureMixerClient;
import java.util.List;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PackRepository.class)
public abstract class PackManagerMixin {
    @Inject(method="openAllSelected",at=@At("RETURN"),cancellable=true)
    private void texturemixer$packs(CallbackInfoReturnable<List<PackResources>> callback){
        var service=TextureMixerClient.SERVICE;
        var client=net.minecraft.client.Minecraft.getInstance();
        if(client!=null&&client.getResourcePackRepository()==(Object)this){
            var packs=service==null?callback.getReturnValue():service.resourcePacks(callback.getReturnValue());
            var result=new java.util.ArrayList<>(packs);result.add(com.fullyx.texturemixer.client.UiFonts.pack(client.getVanillaPackResources()));
            callback.setReturnValue(result);
        }
    }
}
