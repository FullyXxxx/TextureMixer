package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.RenderScope;
import java.util.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ModelManager.class)
public abstract class PreviewStateMapMixin {
    @Inject(method="createBlockStateToModelDispatch",at=@At("HEAD"),cancellable=true)
    private static void texturemixer$partialPreview(Map<BlockState,BlockStateModel> models,BlockStateModel missing,CallbackInfoReturnable<Map<BlockState,BlockStateModel>> callback){
        if(RenderScope.currentArena()!=null)callback.setReturnValue(new IdentityHashMap<>(models));
    }
}
