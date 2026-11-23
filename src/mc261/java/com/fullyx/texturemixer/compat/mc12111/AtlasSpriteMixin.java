package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.RenderScope;
import java.util.Map;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(AtlasManager.class)
public abstract class AtlasSpriteMixin {
    @Shadow private Map<SpriteId,TextureAtlasSprite> spriteLookup;
    @ModifyVariable(method="get",at=@At("HEAD"),argsOnly=true)
    private SpriteId texturemixer$source(SpriteId original){
        String prefix=RenderScope.currentPrefix();if(prefix==null||original.texture().getNamespace().startsWith("texturemixer_"))return original;
        var id=original.texture();SpriteId mapped=new SpriteId(original.atlasLocation(),Identifier.fromNamespaceAndPath(prefix+id.getNamespace(),id.getPath()));
        return spriteLookup.containsKey(mapped)?mapped:original;
    }
}
