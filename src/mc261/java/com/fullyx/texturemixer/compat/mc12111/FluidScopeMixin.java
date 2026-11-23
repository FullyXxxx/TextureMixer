package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FluidRenderer.class)
public abstract class FluidScopeMixin {
    @WrapMethod(method = "tesselate")
    private void texturemixer$scope(BlockAndTintGetter world, BlockPos pos, FluidRenderer.Output output,
                                   BlockState block, FluidState fluid, Operation<Void> original) {
        var service = TextureMixerClient.SERVICE;
        var type = fluid.getType();
        String target = type == Fluids.WATER || type == Fluids.FLOWING_WATER ? "minecraft:water"
                : type == Fluids.LAVA || type == Fluids.FLOWING_LAVA ? "minecraft:lava" : null;
        String prefix = service == null || target == null ? null : service.prefixForBlock(target);
        RenderScope.in(prefix, () -> { original.call(world, pos, output, block, fluid); return null; });
    }

    @WrapOperation(method = "tesselate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/FluidStateModelSet;get(Lnet/minecraft/world/level/material/FluidState;)Lnet/minecraft/client/renderer/block/FluidModel;"))
    private FluidModel texturemixer$model(FluidStateModelSet models, FluidState state, Operation<FluidModel> original) {
        FluidModel model = original.call(models, state);
        if (RenderScope.currentPrefix() == null) return model;
        return new FluidModel(model.layer(), texturemixer$material(model.stillMaterial()),
                texturemixer$material(model.flowingMaterial()), texturemixer$material(model.overlayMaterial()), model.tintSource());
    }

    @Unique
    private static Material.Baked texturemixer$material(Material.Baked original) {
        if (original == null) return null;
        var sprite = original.sprite();
        var selected = Minecraft.getInstance().getAtlasManager().get(new SpriteId(sprite.atlasLocation(), sprite.contents().name()));
        return selected == sprite ? original : new Material.Baked(selected, original.forceTranslucent());
    }
}
