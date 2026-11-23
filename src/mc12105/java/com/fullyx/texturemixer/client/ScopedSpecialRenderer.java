package com.fullyx.texturemixer.client;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.*;
import java.util.function.Consumer;
import org.joml.Vector3fc;
public final class ScopedSpecialRenderer<T> implements SpecialModelRenderer<T> {
    private final SpecialModelRenderer<T> delegate;
    private final String prefix,arena;
    @SuppressWarnings("unchecked")
    public ScopedSpecialRenderer(SpecialModelRenderer<?> delegate,String prefix,String arena){this.delegate=(SpecialModelRenderer<T>)delegate;this.prefix=prefix;this.arena=arena;}
    public void render(T data,ItemDisplayContext context,MatrixStack matrices,VertexConsumerProvider vertices,int light,int overlay,boolean glint){
        RenderScope.in(prefix,()->RenderScope.arena(arena,()->{delegate.render(data,context,matrices,vertices,light,overlay,glint);return null;}));
    }
    public T getData(ItemStack stack){return delegate.getData(stack);}
}
