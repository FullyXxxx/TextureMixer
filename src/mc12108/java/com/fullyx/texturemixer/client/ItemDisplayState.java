package com.fullyx.texturemixer.client;

import java.util.function.Consumer;
import net.minecraft.client.render.item.KeyedItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import net.minecraft.client.render.VertexConsumerProvider;

/** Include the resting first-person yaw before the model's own hand transform. */
class ItemDisplayState extends KeyedItemRenderState {
    private final boolean hand,crossbow;
    ItemDisplayState(boolean hand,boolean crossbow){this.hand=hand;this.crossbow=crossbow;}
    @Override public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light, int overlay) {
        matrices.push();
        if(hand)matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45));
        if(crossbow)matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30));
        super.render(matrices, vertices, light, overlay);
        matrices.pop();
    }
    @Override public void load(Consumer<Vector3fc> consumer) {
        super.load(vertex -> {
            Vector3f position=new Vector3f(vertex);
            if(crossbow)position.rotateX((float)Math.toRadians(-30));
            if(hand)position.rotateY((float)Math.toRadians(45));
            consumer.accept(position);
        });
    }
}
