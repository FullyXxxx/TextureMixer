package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.RenderScope;
import java.util.function.Function;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(SpriteId.class)
public abstract class SpriteIdentifierMixin {
    @Shadow @Final private Identifier atlasLocation;
    @Inject(method="renderType",at=@At("HEAD"),cancellable=true)
    private void texturemixer$preview(Function<Identifier,RenderType> factory,CallbackInfoReturnable<RenderType> callback){
        if(RenderScope.currentArena()!=null)callback.setReturnValue(factory.apply(RenderScope.atlas(atlasLocation)));
    }
}
