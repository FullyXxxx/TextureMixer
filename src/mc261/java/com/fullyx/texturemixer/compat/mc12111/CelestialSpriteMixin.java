package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.RenderScope;
import java.util.Map;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(TextureAtlas.class)
public abstract class CelestialSpriteMixin {
    @Shadow private Map<Identifier,TextureAtlasSprite> texturesByName;
    @ModifyVariable(method="getSprite",at=@At("HEAD"),argsOnly=true)
    private Identifier texturemixer$source(Identifier original){
        String prefix=RenderScope.currentPrefix();if(prefix==null||original.getNamespace().startsWith("texturemixer_"))return original;
        Identifier mapped=Identifier.fromNamespaceAndPath(prefix+original.getNamespace(),original.getPath());return texturesByName.containsKey(mapped)?mapped:original;
    }
}
