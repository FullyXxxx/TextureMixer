package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.RenderScope;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.util.ModelIdentifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BakedModelManager.class)
public abstract class PreviewStateMapMixin {
    @Inject(method="method_45892",at=@At("HEAD"),cancellable=true)
    private static void texturemixer$previewState(Map<ModelIdentifier,BakedModel> models,BakedModel missing,
            Map<BlockState,BakedModel> states,BlockState state,CallbackInfo callback){
        if(RenderScope.currentArena()!=null&&!models.containsKey(BlockModels.getModelId(state)))callback.cancel();
    }
}
