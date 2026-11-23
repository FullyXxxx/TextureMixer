package com.fullyx.texturemixer.sky;

import com.fullyx.texturemixer.client.VersionedIdentifiers;

import com.fullyx.texturemixer.client.NativeCompat;
import com.fullyx.texturemixer.compat.mc12111.RenderPipelinesAccess;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import java.util.Locale;
import java.util.OptionalInt;

final class SkyDrawBackend implements AutoCloseable {
    private static final RenderPipeline[] PIPELINES=new RenderPipeline[SkyScene.Blend.values().length];
    static{
        for(SkyScene.Blend blend:SkyScene.Blend.values())PIPELINES[blend.ordinal()]=RenderPipelinesAccess.texturemixer$register(RenderPipeline.builder()
            .withLocation(VersionedIdentifiers.of("texturemixer","pipeline/sky_"+blend.name().toLowerCase(Locale.ROOT)))
            .withVertexShader(VersionedIdentifiers.of("texturemixer","core/sky")).withFragmentShader(VersionedIdentifiers.of("texturemixer","core/sky"))
            .withShaderDefine("BLEND_MODE",blend.ordinal()).withSampler("Sampler0").withSampler("DestSampler")
            .withUniform("DynamicTransforms",UniformType.UNIFORM_BUFFER).withUniform("Projection",UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR,VertexFormat.DrawMode.QUADS)
            .withCull(false).withDepthWrite(false).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).build());
    }
    static void initialize(){}

    static double cameraY(Camera camera){return camera.getCameraPos().y;}
    static float partialTick(net.minecraft.client.MinecraftClient client){return client.getRenderTickCounter().getTickProgress(false);}
    private GpuBuffer cube;
    private GpuBuffer celestialQuad;
    private GpuTexture destination;
    private GpuTextureView destinationView;

    static void vertex(BufferBuilder builder,float x,float y,float z,float u,float v){
        builder.vertex(x,y,z).texture(u,v).color(-1);
    }

    boolean draw(Framebuffer framebuffer,AbstractTexture texture,Matrix4f matrix,float alpha,SkyScene.Blend blend,boolean celestial){
        GpuTexture color=framebuffer.getColorAttachment();
        if(color==null)return false;
        int width=color.getWidth(0),height=color.getHeight(0);
        if(destination==null||destination.getWidth(0)!=width||destination.getHeight(0)!=height){
            if(destinationView!=null)destinationView.close();
            if(destination!=null)destination.close();
            destination=RenderSystem.getDevice().createTexture(()->"TextureMixer sky blend input",GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_TEXTURE_BINDING,TextureFormat.RGBA8,width,height,1,1);
            destinationView=RenderSystem.getDevice().createTextureView(destination);
        }
        GpuBuffer vertices=geometry(celestial);
        int count=celestial?6:36;
        var indices=RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
        var index=indices.getIndexBuffer(count);
        var uniforms=NativeCompat.skyUniforms(matrix,alpha);
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(color,destination,0,0,0,0,0,width,height);
        try(var pass=encoder.createRenderPass(()->"TextureMixer sky",framebuffer.getColorAttachmentView(),OptionalInt.empty())){
            pass.setPipeline(PIPELINES[blend.ordinal()]);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms",uniforms);
            NativeCompat.bindSkyTextures(pass,texture,destinationView);
            pass.setVertexBuffer(0,vertices);
            pass.setIndexBuffer(index,indices.getIndexType());
            pass.drawIndexed(0,0,count,1);
        }
        return true;
    }

    private static GpuBuffer createVertices(boolean celestial){
        try(var allocator=new BufferAllocator((celestial?4:24)*VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize())){
            var builder=new BufferBuilder(allocator,VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
            if(celestial)SkySceneRenderer.celestialVertices(builder);else SkySceneRenderer.cubeVertices(builder);
            try(var built=builder.end()){
                return RenderSystem.getDevice().createBuffer(()->"TextureMixer sky geometry",GpuBuffer.USAGE_VERTEX,built.getBuffer());
            }
        }
    }

    private GpuBuffer geometry(boolean celestial){
        if(celestial){
            if(celestialQuad==null)celestialQuad=createVertices(true);
            return celestialQuad;
        }
        if(cube==null)cube=createVertices(false);
        return cube;
    }
    @Override public void close(){
        if(cube!=null){cube.close();cube=null;}
        if(celestialQuad!=null){celestialQuad.close();celestialQuad=null;}
        if(destinationView!=null){destinationView.close();destinationView=null;}
        if(destination!=null){destination.close();destination=null;}
    }
}
