package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(BlockEntityRenderManager.class)
public abstract class BlockEntityScopeMixin {
    @WrapMethod(method="render")
    private void texturemixer$blockEntity(BlockEntityRenderState state,MatrixStack matrices,OrderedRenderCommandQueue queue,CameraRenderState camera,Operation<Void> original){
        if(RenderScope.currentArena()!=null){original.call(state,matrices,queue,camera);return;}
        var service=TextureMixerClient.SERVICE;
        String prefix=service==null?null:service.prefixForBlock(Registries.BLOCK.getId(state.blockState.getBlock()).toString());
        RenderScope.in(prefix,()->{original.call(state,matrices,queue,camera);return null;});
    }
}
