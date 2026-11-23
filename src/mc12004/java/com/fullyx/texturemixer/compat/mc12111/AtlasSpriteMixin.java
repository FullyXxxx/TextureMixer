package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.RenderScope;
import net.minecraft.client.texture.*;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import java.util.Map;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(AtlasManager.class)
public abstract class AtlasSpriteMixin {
    @Shadow private Map<SpriteIdentifier,Sprite> sprites;
    @ModifyVariable(method="getSprite",at=@At("HEAD"),argsOnly=true)
    private SpriteIdentifier texturemixer$source(SpriteIdentifier original){
        String prefix=RenderScope.currentPrefix();if(prefix==null||original.getTextureId().getNamespace().startsWith("texturemixer_"))return original;
        var id=original.getTextureId();SpriteIdentifier mapped=new SpriteIdentifier(original.getAtlasId(),new Identifier(prefix+id.getNamespace(),id.getPath()));
        return sprites.containsKey(mapped)?mapped:original;
    }
}
