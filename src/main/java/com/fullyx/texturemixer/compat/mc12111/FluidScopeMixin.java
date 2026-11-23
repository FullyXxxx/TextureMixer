package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.VersionedIdentifiers;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.impl.client.rendering.fluid.FluidRenderHandlerInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric substitutes these sprites after the native fluid renderer reads its fields. */
@Mixin(value = FluidRenderHandlerInfo.class, remap = false)
public abstract class FluidScopeMixin {
    @Shadow @Final public Sprite[] sprites;
    @Shadow public Sprite overlaySprite;
    @Shadow public boolean hasOverlay;

    @Inject(method = "setup", at = @At("RETURN"))
    private void texturemixer$fluid(FluidRenderHandler handler, BlockRenderView world, BlockPos pos,
                                   FluidState fluid, CallbackInfo callback) {
        var service = TextureMixerClient.SERVICE;
        if (service == null) return;
        var type = fluid.getFluid();
        String name = type == Fluids.WATER || type == Fluids.FLOWING_WATER ? "water"
                : type == Fluids.LAVA || type == Fluids.FLOWING_LAVA ? "lava" : null;
        if (name == null) return;
        String prefix = service.prefixForBlock("minecraft:" + name);
        if (prefix == null) return;

        sprites[0] = texturemixer$pick(prefix, name + "_still", sprites[0]);
        sprites[1] = texturemixer$pick(prefix, name + "_flow", sprites[1]);
        if (hasOverlay && name.equals("water")) {
            overlaySprite = texturemixer$pick(prefix, "water_overlay", overlaySprite);
        }
    }

    @Unique
    private static Sprite texturemixer$pick(String prefix, String path, Sprite fallback) {
        var id = VersionedIdentifiers.of(prefix + "minecraft", "block/" + path);
        var spriteId = new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, id);
        var sprite = MinecraftClient.getInstance().getAtlasManager().getSprite(spriteId);
        return sprite != null && sprite.getContents().getId().equals(id) ? sprite : fallback;
    }
}
