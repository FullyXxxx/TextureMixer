package com.fullyx.texturemixer.client;

import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.render.VertexConsumerProvider;

/** Include the resting first-person yaw before the model's own hand transform. */
class ItemDisplayState extends ItemRenderState {
    private final boolean hand,crossbow;
    ItemDisplayState(boolean hand,boolean crossbow){this.hand=hand;this.crossbow=crossbow;}
    @Override public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light, int overlay) {
        matrices.push();
        if(hand)matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45));
        if(crossbow)matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30));
        super.render(matrices, vertices, light, overlay);
        matrices.pop();
    }
    void addModelKey(Object ignored){}
    void markAnimated(){}
}
