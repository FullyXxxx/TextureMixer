package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(MapRenderer.class)
public abstract class MapDecorationMixin {
    @WrapMethod(method="extractRenderState")
    private void texturemixer$decorations(MapId id,MapItemSavedData state,MapRenderState renderState,Operation<Void> original){
        var service=TextureMixerClient.SERVICE;String prefix=RenderScope.currentPrefix();
        if(prefix==null&&service!=null)prefix=service.prefixForItem("minecraft:filled_map");
        RenderScope.in(prefix,()->{original.call(id,state,renderState);return null;});
    }
}
