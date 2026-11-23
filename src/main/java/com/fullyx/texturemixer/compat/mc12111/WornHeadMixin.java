package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(HeadFeatureRenderer.class)
public abstract class WornHeadMixin {
    @WrapMethod(method="render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/LivingEntityRenderState;FF)V")
    private void texturemixer$head(MatrixStack matrices,OrderedRenderCommandQueue queue,int light,LivingEntityRenderState state,float yaw,float pitch,Operation<Void> original){
        var data=(WornHeadState)state;RenderScope.in(data.texturemixer$headPrefix(),()->RenderScope.arena(data.texturemixer$headArena(),()->{
            original.call(matrices,queue,light,state,yaw,pitch);return null;
        }));
    }
}
