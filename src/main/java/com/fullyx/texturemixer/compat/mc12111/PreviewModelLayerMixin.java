package com.fullyx.texturemixer.compat.mc12111;
import net.minecraft.client.render.item.model.BasicItemModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.*;
import net.minecraft.item.ItemStack;
import java.util.*;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(BasicItemModel.class)
public abstract class PreviewModelLayerMixin {
    @Inject(method="findRenderLayerGetter",at=@At("HEAD"),cancellable=true)
    private static void texturemixer$layer(List<BakedQuad> quads,CallbackInfoReturnable<Function<ItemStack,RenderLayer>> callback){
        if(quads.isEmpty())return;
        var atlas=quads.getFirst().sprite().getAtlasId();
        if(atlas.getNamespace().equals("texturemixer")&&atlas.getPath().startsWith("preview/"))
            callback.setReturnValue(stack->com.fullyx.texturemixer.client.NativeCompat.translucentItemLayer(atlas));
    }
}
