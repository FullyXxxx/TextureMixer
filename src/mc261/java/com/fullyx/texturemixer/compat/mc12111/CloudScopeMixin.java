package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.sky.*;
import java.util.Optional;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(CloudRenderer.class)
public abstract class CloudScopeMixin {
    @Shadow private CloudRenderer.TextureData texture;
    @Shadow private boolean needsRebuild;
    @Unique private CloudRenderer.TextureData texturemixer$base;
    @Unique private boolean texturemixer$captured;
    @Inject(method="apply",at=@At("RETURN"))
    private void texturemixer$base(Optional<CloudRenderer.TextureData> prepared,net.minecraft.server.packs.resources.ResourceManager manager,net.minecraft.util.profiling.ProfilerFiller profiler,CallbackInfo callback){
        texturemixer$base=texture;texturemixer$captured=true;
    }
    @Inject(method="render",at=@At("HEAD"))
    private void texturemixer$clouds(CallbackInfo callback){
        if(!texturemixer$captured){texturemixer$base=texture;texturemixer$captured=true;}
        SkyScene scene=SkySceneRenderer.current();CloudRenderer.TextureData selected=scene==null?texturemixer$base:CloudBindings.cells(scene);
        if(texture!=selected){texture=selected;needsRebuild=true;}
    }
}
