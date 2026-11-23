package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityScopeMixin {
    @WrapMethod(method="submit")
    private void texturemixer$blockEntity(BlockEntityRenderState state,PoseStack matrices,SubmitNodeCollector queue,CameraRenderState camera,Operation<Void> original){
        if(RenderScope.currentArena()!=null){original.call(state,matrices,queue,camera);return;}
        var service=TextureMixerClient.SERVICE;
        String prefix=service==null?null:service.prefixForBlock(BuiltInRegistries.BLOCK.getKey(((BlockEntityStateAccess)state).texturemixer$blockState().getBlock()).toString());
        RenderScope.in(prefix,()->{original.call(state,matrices,queue,camera);return null;});
    }
}
