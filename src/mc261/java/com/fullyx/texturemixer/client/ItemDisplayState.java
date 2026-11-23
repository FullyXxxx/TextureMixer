package com.fullyx.texturemixer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Include the resting first-person yaw before the model's own hand transform. */
class ItemDisplayState extends TrackingItemStackRenderState {
    private final boolean hand,crossbow;
    ItemDisplayState(boolean hand,boolean crossbow){this.hand=hand;this.crossbow=crossbow;}
    @Override public void submit(PoseStack matrices, SubmitNodeCollector queue, int light, int overlay, int outline) {
        matrices.pushPose();
        if(hand)matrices.mulPose(Axis.YP.rotationDegrees(45));
        if(crossbow)matrices.mulPose(Axis.XP.rotationDegrees(-30));
        super.submit(matrices, queue, light, overlay, outline);
        matrices.popPose();
    }
    @Override public void visitExtents(Consumer<Vector3fc> consumer) {
        super.visitExtents(vertex -> {
            Vector3f position=new Vector3f(vertex);
            if(crossbow)position.rotateX((float)Math.toRadians(-30));
            if(hand)position.rotateY((float)Math.toRadians(45));
            consumer.accept(position);
        });
    }
}
