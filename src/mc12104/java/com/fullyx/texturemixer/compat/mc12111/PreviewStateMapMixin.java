package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.RenderScope;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BakedModelManager.class)
public abstract class PreviewStateMapMixin {
    @Inject(method="toStateMap",at=@At("HEAD"),cancellable=true)
    private static void texturemixer$partialPreview(Map<ModelIdentifier,BakedModel> models,BakedModel missing,
            CallbackInfoReturnable<Map<BlockState,BakedModel>> callback){
        if(RenderScope.currentArena()==null)return;
        Map<BlockState,BakedModel> selected=new IdentityHashMap<>();
        for(var block:Registries.BLOCK)for(BlockState state:block.getStateManager().getStates()){
            var model=models.get(BlockModels.getModelId(state));
            if(model!=null)selected.put(state,model);
        }
        callback.setReturnValue(selected);
    }
}
