package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.sky.*;
import net.minecraft.client.render.CloudRenderer;
import java.util.Optional;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(CloudRenderer.class)
public abstract class CloudScopeMixin {
    @Shadow private CloudRenderer.CloudCells cells;
    @Shadow private boolean rebuild;
    @Unique private CloudRenderer.CloudCells texturemixer$base;
    @Unique private boolean texturemixer$captured;
    @Inject(method="apply",at=@At("RETURN"))
    private void texturemixer$base(Optional<CloudRenderer.CloudCells> prepared,net.minecraft.resource.ResourceManager manager,net.minecraft.util.profiler.Profiler profiler,CallbackInfo callback){
        texturemixer$base=cells;texturemixer$captured=true;
    }
    @Inject(method="renderClouds",at=@At("HEAD"))
    private void texturemixer$clouds(CallbackInfo callback){
        if(!texturemixer$captured){texturemixer$base=cells;texturemixer$captured=true;}
        SkyScene scene=SkySceneRenderer.current();CloudRenderer.CloudCells selected=scene==null?texturemixer$base:CloudBindings.cells(scene);
        if(cells!=selected){cells=selected;rebuild=true;}
    }
}
