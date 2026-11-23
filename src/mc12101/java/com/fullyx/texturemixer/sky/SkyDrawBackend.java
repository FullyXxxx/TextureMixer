package com.fullyx.texturemixer.sky;

import com.fullyx.texturemixer.client.VersionedIdentifiers;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

final class SkyDrawBackend implements AutoCloseable {
    private static ShaderProgram program;

    private VertexBuffer cube;
    private VertexBuffer celestialQuad;
    private SimpleFramebuffer destination;

    static void initialize(){
        CoreShaderRegistrationCallback.EVENT.register(context->
                context.register(VersionedIdentifiers.of("texturemixer","sky_pre12102"),
                        VertexFormats.POSITION_TEXTURE_COLOR,shader->program=shader));
    }
    static double cameraY(Camera camera){return camera.getPos().y;}
    static float partialTick(net.minecraft.client.MinecraftClient client){return client.getRenderTickCounter().getTickDelta(false);}

    static void vertex(BufferBuilder builder,float x,float y,float z,float u,float v){
        builder.vertex(x,y,z).texture(u,v).color(-1);
    }

    boolean draw(Framebuffer framebuffer,AbstractTexture texture,Matrix4f matrix,float alpha,SkyScene.Blend blend,boolean celestial){
        if(framebuffer.getColorAttachment()<=0)return false;
        if(program==null)return false;

        int width=framebuffer.textureWidth,height=framebuffer.textureHeight;
        int previousRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean depthTest=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blending=GL11.glIsEnabled(GL11.GL_BLEND);
        int previousTexture=RenderSystem.getShaderTexture(0);
        try{
            if(destination==null||destination.textureWidth!=width||destination.textureHeight!=height){
                if(destination!=null)destination.delete();
                destination=new SimpleFramebuffer(width,height,false,MinecraftClient.IS_SYSTEM_MAC);
            }
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,framebuffer.fbo);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,destination.fbo);
            GlStateManager._glBlitFrameBuffer(0,0,width,height,0,0,width,height,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,framebuffer.fbo);

            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderTexture(0,texture.getGlId());
            program.addSampler("DestSampler",destination.getColorAttachment());
            program.getUniformOrDefault("BlendMode").set(blend.ordinal());
            program.getUniformOrDefault("LayerAlpha").set(alpha);
            var geometry=geometry(celestial);
            geometry.bind();
            geometry.draw(matrix,RenderSystem.getProjectionMatrix(),program);
            return true;
        }finally{
            VertexBuffer.unbind();
            RenderSystem.setShaderTexture(0,previousTexture);
            RenderSystem.depthMask(depthWrite);
            if(depthTest)RenderSystem.enableDepthTest();
            if(cull)RenderSystem.enableCull();
            if(blending)RenderSystem.enableBlend();
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,previousRead);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,previousDraw);
        }
    }

    private VertexBuffer geometry(boolean celestial){
        if(celestial){
            if(celestialQuad==null)celestialQuad=createVertices(true);
            return celestialQuad;
        }
        if(cube==null)cube=createVertices(false);
        return cube;
    }

    private static VertexBuffer createVertices(boolean celestial){
        try(var allocator=new BufferAllocator((celestial?4:24)*VertexFormats.POSITION_TEXTURE_COLOR.getVertexSizeByte())){
            var builder=new BufferBuilder(allocator,VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
            if(celestial)SkySceneRenderer.celestialVertices(builder);else SkySceneRenderer.cubeVertices(builder);
            var buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);
            try{
                buffer.bind();
                buffer.upload(builder.end());
                return buffer;
            }catch(RuntimeException error){
                buffer.close();
                throw error;
            }
        }
    }

    @Override public void close(){
        if(cube!=null){cube.close();cube=null;}
        if(celestialQuad!=null){celestialQuad.close();celestialQuad=null;}
        if(destination!=null){destination.delete();destination=null;}
    }
}
