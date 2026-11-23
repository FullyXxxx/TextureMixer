package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.client.render.model.BlockStatesLoader;
import net.minecraft.registry.DefaultedRegistry;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockStatesLoader.class)
public abstract class LegacyBlockStatesMixin {
    @Shadow @Final private Map<Identifier,?> blockStates;

    @WrapOperation(method="load",at=@At(value="INVOKE",target="Lnet/minecraft/registry/DefaultedRegistry;iterator()Ljava/util/Iterator;"))
    private Iterator<Block> texturemixer$previewOnly(DefaultedRegistry<Block> registry,Operation<Iterator<Block>> original){
        if(RenderScope.currentArena()==null)return original.call(registry);
        return registry.stream().filter(block->blockStates.containsKey(
                registry.getId(block).withPath(path->"blockstates/"+path+".json"))).iterator();
    }
}
