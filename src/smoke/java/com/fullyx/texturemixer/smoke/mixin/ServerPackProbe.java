package com.fullyx.texturemixer.smoke.mixin;
import com.fullyx.texturemixer.smoke.SmokeControls;
import net.minecraft.resource.*;
import java.util.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ResourcePackManager.class)
public abstract class ServerPackProbe {
    @Inject(method="createResourcePacks",at=@At("HEAD"))
    private void texturemixer$countReload(CallbackInfoReturnable<List<ResourcePack>> callback){
        var client=net.minecraft.client.MinecraftClient.getInstance();if(client!=null&&client.getResourcePackManager()==(Object)this)SmokeControls.reloads++;
    }
    @Inject(method="providePackProfiles",at=@At("RETURN"),cancellable=true)
    private void texturemixer$server(CallbackInfoReturnable<Map<String,ResourcePackProfile>> callback){
        if(SmokeControls.serverPack){var profiles=new TreeMap<>(callback.getReturnValue());var profile=SmokeControls.profile();profiles.put(profile.getId(),profile);callback.setReturnValue(profiles);}
    }
}
