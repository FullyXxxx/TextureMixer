package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.RenderScope;
import com.fullyx.texturemixer.sky.SkyScene;
import com.fullyx.texturemixer.sky.SkySceneRenderer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SkyRendering.class)
public abstract class WorldSkyMixin {
    @WrapMethod(method="bindTexture")
    private AbstractTexture texturemixer$skyTexture(Identifier texture,Operation<AbstractTexture> original){
        var scene=SkySceneRenderer.current();
        if(scene==null)return original.call(texture);
        return RenderScope.in(SkyScene.namespacePrefix(scene.dimension()),()->original.call(texture));
    }
}
