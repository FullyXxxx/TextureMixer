package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(CustomHeadLayer.class)
public abstract class WornHeadMixin {
    @WrapMethod(method="submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V")
    private void texturemixer$head(PoseStack matrices,SubmitNodeCollector queue,int light,LivingEntityRenderState state,float yaw,float pitch,Operation<Void> original){
        var data=(WornHeadState)state;RenderScope.in(data.texturemixer$headPrefix(),()->RenderScope.arena(data.texturemixer$headArena(),()->{
            original.call(matrices,queue,light,state,yaw,pitch);return null;
        }));
    }
}
