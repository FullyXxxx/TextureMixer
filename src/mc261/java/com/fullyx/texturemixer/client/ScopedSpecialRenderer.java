package com.fullyx.texturemixer.client;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
public final class ScopedSpecialRenderer<T> implements SpecialModelRenderer<T> {
    private final SpecialModelRenderer<T> delegate;
    private final String prefix,arena;
    @SuppressWarnings("unchecked")
    public ScopedSpecialRenderer(SpecialModelRenderer<?> delegate,String prefix,String arena){this.delegate=(SpecialModelRenderer<T>)delegate;this.prefix=prefix;this.arena=arena;}
    public void submit(T data,PoseStack matrices,SubmitNodeCollector queue,int light,int overlay,boolean glint,int color){
        RenderScope.in(prefix,()->RenderScope.arena(arena,()->{delegate.submit(data,matrices,queue,light,overlay,glint,color);return null;}));
    }
    public void getExtents(Consumer<Vector3fc> consumer){NativeCompat.vertices(delegate,consumer);}
    public void collectVertices(java.util.Set<org.joml.Vector3f> vertices){
        NativeCompat.vertices(delegate,vertex->vertices.add(new org.joml.Vector3f(vertex)));
    }
    public T extractArgument(ItemStack stack){return delegate.extractArgument(stack);}
}
