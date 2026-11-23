package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.RenderScope;
import com.fullyx.texturemixer.client.PreviewModelArena;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import java.util.function.Function;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(SpriteIdentifier.class)
public abstract class SpriteIdentifierMixin {
    @Shadow @Final private Identifier atlas;
    @Inject(method="getRenderLayer",at=@At("HEAD"),cancellable=true)
    private void texturemixer$preview(Function<Identifier,RenderLayer> factory,CallbackInfoReturnable<RenderLayer> callback){
        if(RenderScope.currentArena()!=null)callback.setReturnValue(factory.apply(RenderScope.atlas(atlas)));
    }

    @Inject(method="getSprite",at=@At("HEAD"),cancellable=true)
    private void texturemixer$previewSprite(CallbackInfoReturnable<Sprite> callback){
        var sprite=PreviewModelArena.activeSprite((SpriteIdentifier)(Object)this);
        if(sprite!=null)callback.setReturnValue(sprite);
    }
}
