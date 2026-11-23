package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.BlockState;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(BlockColors.class)
public abstract class BlockColorScopeMixin {
    @WrapMethod(method="getColor")
    private int texturemixer$color(BlockState state,BlockRenderView world,BlockPos pos,int tint,Operation<Integer> original){
        var service=TextureMixerClient.SERVICE;String prefix=service==null?null:service.prefixForBlock(Registries.BLOCK.getId(state.getBlock()).toString());
        if(RenderScope.currentArena()!=null)return original.call(state,world,pos,tint);
        return RenderScope.in(prefix,()->original.call(state,world,pos,tint));
    }
}
