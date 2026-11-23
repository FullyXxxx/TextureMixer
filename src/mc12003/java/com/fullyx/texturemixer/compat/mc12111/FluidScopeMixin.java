package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.TextureMixerClient;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.impl.client.rendering.fluid.FluidRendererHookContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric already selects the fluid sprites for this renderer generation. */
@Mixin(value=FluidRendererHookContainer.class,remap=false)
public abstract class FluidScopeMixin {
    @Shadow @Final public Sprite[] sprites;
    @Shadow public Sprite overlay;
    @Shadow public boolean hasOverlay;

    @Inject(method="getSprites",at=@At("RETURN"))
    private void texturemixer$fluid(BlockRenderView world,BlockPos pos,FluidState fluid,CallbackInfo callback){
        var service = TextureMixerClient.SERVICE;
        if (service == null) return;
        var type = fluid.getFluid();
        String name = type == Fluids.WATER || type == Fluids.FLOWING_WATER ? "water"
                : type == Fluids.LAVA || type == Fluids.FLOWING_LAVA ? "lava" : null;
        if (name == null) return;
        String prefix = service.prefixForBlock("minecraft:" + name);
        if (prefix == null) return;
        var atlas = MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        sprites[0] = texturemixer$pick(atlas, prefix, name + "_still", sprites[0]);
        sprites[1] = texturemixer$pick(atlas, prefix, name + "_flow", sprites[1]);
        if (hasOverlay && name.equals("water")) overlay = texturemixer$pick(atlas, prefix, "water_overlay", overlay);
    }

    @Unique
    private static Sprite texturemixer$pick(SpriteAtlasTexture atlas, String prefix, String path, Sprite fallback) {
        var id = new Identifier(prefix + "minecraft", "block/" + path);
        Sprite sprite = atlas.getSprite(id);
        return sprite != null && sprite.getContents().getId().equals(id) ? sprite : fallback;
    }
}
