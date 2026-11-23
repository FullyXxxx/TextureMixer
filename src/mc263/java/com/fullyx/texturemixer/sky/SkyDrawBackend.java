package com.fullyx.texturemixer.sky;

import com.fullyx.texturemixer.client.NativeCompat;

import com.fullyx.texturemixer.compat.mc12111.RenderPipelinesAccess;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.*;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import org.joml.Matrix4f;
import java.util.Locale;
import java.util.Optional;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

final class SkyDrawBackend implements AutoCloseable {
    private static final RenderPipeline[] PIPELINES=new RenderPipeline[SkyScene.Blend.values().length];
    static{
        for(SkyScene.Blend blend:SkyScene.Blend.values())PIPELINES[blend.ordinal()]=RenderPipelinesAccess.texturemixer$register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("texturemixer","pipeline/sky_"+blend.name().toLowerCase(Locale.ROOT)))
            .withVertexShader(Identifier.fromNamespaceAndPath("texturemixer","core/sky_pearl")).withFragmentShader(Identifier.fromNamespaceAndPath("texturemixer","core/sky_pearl"))
            .withShaderDefine("BLEND_MODE",blend.ordinal())
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("Sampler0",UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("DestSampler",UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("DynamicTransforms",UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection",UniformType.UNIFORM_BUFFER).build())
            .withVertexBinding(0,DefaultVertexFormat.POSITION_TEX_COLOR).withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false).withDepthStencilState(java.util.Optional.empty()).build());
    }
    static void initialize(){}

    static double cameraY(Camera camera){return camera.position().y;}
    static float partialTick(DeltaTracker ticks){return ticks.getGameTimeDeltaPartialTick(false);}
    private GpuBuffer cube;
    private GpuBuffer celestialQuad;
    private GpuTexture destination;
    private GpuTextureView destinationView;

    boolean draw(RenderTarget framebuffer,AbstractTexture texture,Matrix4f matrix,float alpha,SkyScene.Blend blend,boolean celestial){
        GpuTexture color=framebuffer.getColorTexture();
        if(color==null)return false;
        int width=color.getWidth(0),height=color.getHeight(0);
        if(destination==null||destination.getWidth(0)!=width||destination.getHeight(0)!=height){
            if(destinationView!=null)destinationView.close();
            if(destination!=null)destination.close();
            destination=RenderSystem.getDevice().createTexture(()->"TextureMixer sky blend input",GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_TEXTURE_BINDING,GpuFormat.RGBA8_UNORM,width,height,1,1);
            destinationView=RenderSystem.getDevice().createTextureView(destination);
        }
        GpuBuffer vertices=geometry(celestial);
        int count=celestial?6:36;
        var indices=RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        var index=indices.getBuffer(count);
        var uniforms=NativeCompat.skyUniforms(matrix,alpha);
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(color,destination,0,0,0,0,0,width,height);
        try(var pass=encoder.createRenderPass(()->"TextureMixer sky",framebuffer.getColorTextureView(),Optional.empty())){
            pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINES[blend.ordinal()]));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms",uniforms);
            NativeCompat.bindSkyTextures(pass,texture,destinationView);
            pass.setVertexBuffer(0,vertices.slice());
            pass.setIndexBuffer(index,indices.type());
            pass.drawIndexed(count,1,0,0,0);
        }
        return true;
    }

    private static GpuBuffer createVertices(boolean celestial){
        try(var allocator=new ByteBufferBuilder((celestial?4:24)*DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())){
            var builder=new BufferBuilder(allocator,PrimitiveTopology.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            if(celestial)SkySceneRenderer.celestialVertices(builder);else SkySceneRenderer.cubeVertices(builder);
            try(var built=builder.buildOrThrow()){
                return RenderSystem.getDevice().createBuffer(()->"TextureMixer sky geometry",GpuBuffer.USAGE_VERTEX,built.vertexBuffer());
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
