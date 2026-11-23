package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.VersionedIdentifiers;
import com.fullyx.texturemixer.client.RenderScope;
import net.minecraft.client.texture.*;
import net.minecraft.util.Identifier;
import java.util.Map;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(SpriteAtlasTexture.class)
public abstract class CelestialSpriteMixin {
    @Shadow private Map<Identifier,Sprite> sprites;
    @ModifyVariable(method="getSprite",at=@At("HEAD"),argsOnly=true)
    private Identifier texturemixer$source(Identifier original){
        String prefix=RenderScope.currentPrefix();if(prefix==null||original.getNamespace().startsWith("texturemixer_"))return original;
        Identifier mapped=VersionedIdentifiers.of(prefix+original.getNamespace(),original.getPath());return sprites.containsKey(mapped)?mapped:original;
    }
}
