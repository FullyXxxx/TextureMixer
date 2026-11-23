package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.WornHeadState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.*;
@Mixin(LivingEntityRenderState.class)
public abstract class WornStateMixin implements WornHeadState {
    @Unique private String texturemixer$headPrefix,texturemixer$headArena;
    public String texturemixer$headPrefix(){return texturemixer$headPrefix;}
    public void texturemixer$headPrefix(String value){texturemixer$headPrefix=value;}
    public String texturemixer$headArena(){return texturemixer$headArena;}
    public void texturemixer$headArena(String value){texturemixer$headArena=value;}
}
