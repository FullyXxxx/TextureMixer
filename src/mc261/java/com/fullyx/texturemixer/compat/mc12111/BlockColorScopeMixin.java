package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ModelBlockRenderer.class)
public abstract class BlockColorScopeMixin {
    @WrapMethod(method="computeTintColor")
    private int texturemixer$color(BlockAndTintGetter world,BlockState state,BlockPos pos,int tint,Operation<Integer> original){
        var service=TextureMixerClient.SERVICE;String prefix=service==null?null:service.prefixForBlock(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if(RenderScope.currentArena()!=null)return original.call(world,state,pos,tint);
        return RenderScope.in(prefix,()->original.call(world,state,pos,tint));
    }
}
