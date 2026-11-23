package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.*;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.map.MapState;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(MapRenderer.class)
public abstract class MapDecorationMixin {
    @WrapMethod(method="update")
    private void texturemixer$decorations(MapIdComponent id,MapState state,MapRenderState renderState,Operation<Void> original){
        var service=TextureMixerClient.SERVICE;String prefix=RenderScope.currentPrefix();
        if(prefix==null&&service!=null)prefix=service.prefixForItem("minecraft:filled_map");
        RenderScope.in(prefix,()->{original.call(id,state,renderState);return null;});
    }
}
