package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.RenderScope;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.*;
import java.util.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(BakedModelManager.class)
public abstract class PreviewStateMapMixin {
    @Inject(method="toStateMap",at=@At("HEAD"),cancellable=true)
    private static void texturemixer$partialPreview(Map<BlockState,BlockStateModel> models,BlockStateModel missing,CallbackInfoReturnable<Map<BlockState,BlockStateModel>> callback){
        if(RenderScope.currentArena()!=null)callback.setReturnValue(new IdentityHashMap<>(models));
    }
}
