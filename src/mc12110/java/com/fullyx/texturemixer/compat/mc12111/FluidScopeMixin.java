package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.VersionedIdentifiers;
import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.client.texture.*;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(FluidRenderer.class)
public abstract class FluidScopeMixin {
    @Shadow @Final private Sprite[] lavaSprites;
    @Shadow @Final private Sprite[] waterSprites;
    @Shadow private Sprite waterOverlaySprite;
    @WrapMethod(method="render")
    private void texturemixer$fluid(BlockRenderView world,BlockPos pos,VertexConsumer vertices,BlockState block,FluidState fluid,Operation<Void> original){
        var service = TextureMixerClient.SERVICE;
        var type = fluid.getFluid();
        String target = type == Fluids.WATER || type == Fluids.FLOWING_WATER ? "minecraft:water"
                : type == Fluids.LAVA || type == Fluids.FLOWING_LAVA ? "minecraft:lava" : null;
        String prefix = service == null || target == null ? null : service.prefixForBlock(target);
        RenderScope.in(prefix,()->{original.call(world,pos,vertices,block,fluid);return null;});
    }
    private Sprite texturemixer$pick(String name,Sprite fallback){
        String prefix=RenderScope.currentPrefix();if(prefix==null)return fallback;
        var id = VersionedIdentifiers.of(prefix + "minecraft", "block/" + name);
        var sprite = MinecraftClient.getInstance().getAtlasManager().getSprite(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, id));
        return sprite != null && sprite.getContents().getId().equals(id) ? sprite : fallback;
    }
    private Sprite[] texturemixer$sprites(Sprite[] originals,String name){
        if(RenderScope.currentPrefix()==null)return originals;
        return new Sprite[]{texturemixer$pick(name+"_still",originals[0]),texturemixer$pick(name+"_flow",originals[1])};
    }
    @Redirect(method="render",at=@At(value="FIELD",target="Lnet/minecraft/client/render/block/FluidRenderer;lavaSprites:[Lnet/minecraft/client/texture/Sprite;"))
    private Sprite[] texturemixer$lava(FluidRenderer renderer){return texturemixer$sprites(lavaSprites,"lava");}
    @Redirect(method="render",at=@At(value="FIELD",target="Lnet/minecraft/client/render/block/FluidRenderer;waterSprites:[Lnet/minecraft/client/texture/Sprite;"))
    private Sprite[] texturemixer$water(FluidRenderer renderer){return texturemixer$sprites(waterSprites,"water");}
    @Redirect(method="render",at=@At(value="FIELD",target="Lnet/minecraft/client/render/block/FluidRenderer;waterOverlaySprite:Lnet/minecraft/client/texture/Sprite;"))
    private Sprite texturemixer$waterOverlay(FluidRenderer renderer){return texturemixer$pick("water_overlay",waterOverlaySprite);}
}
