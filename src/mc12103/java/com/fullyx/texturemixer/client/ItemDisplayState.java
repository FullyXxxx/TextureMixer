package com.fullyx.texturemixer.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

final class ItemDisplayState {
    @FunctionalInterface
    interface Layer {
        void render(MatrixStack matrices,VertexConsumerProvider vertices,int light,int overlay);
    }

    private final boolean hand;
    private final boolean crossbow;
    private boolean sideLit;
    private final List<Layer> layers=new ArrayList<>();

    ItemDisplayState(boolean hand,boolean crossbow){
        this.hand=hand;
        this.crossbow=crossbow;
    }
    boolean isSideLit(){return sideLit;}
    void setSideLit(boolean useDepth){sideLit=useDepth;}
    void add(Layer layer){layers.add(layer);}

    void render(MatrixStack matrices,VertexConsumerProvider vertices,int light,int overlay){
        matrices.push();
        try{
            if(hand)matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45));
            if(crossbow)matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30));
            for(Layer layer:layers)layer.render(matrices,vertices,light,overlay);
        }finally{matrices.pop();}
    }
}
