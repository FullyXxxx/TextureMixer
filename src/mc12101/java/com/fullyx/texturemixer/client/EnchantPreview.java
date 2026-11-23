package com.fullyx.texturemixer.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import java.util.*;

/** Native glint layers with a private sampler binding for each preview source. */
public final class EnchantPreview {
    private static final Map<Object,Effect> QUEUED=new IdentityHashMap<>();
    private static Effect current;
    private ArmorStandEntity stand;


    public static final class Effect {
        private final Identifier texture;
        private final boolean armor;
        private RenderLayer armorLayer;

        public Effect(Identifier texture,boolean armor){this.texture=texture;this.armor=armor;}
        public boolean armor(){return armor;}
    }
    public static RenderLayer armorLayer(RenderLayer original){
        if(current==null||!current.armor)return original;
        if(current.armorLayer==null){
            current.armorLayer=NativeCompat.armorGlint(original,current.texture);
        }
        return current.armorLayer;
    }
    public static Identifier itemTexture(Identifier id){
        if(current==null)return id;
        return current.armor?NativeCompat.armorGlintTexture(id,current.texture)
                :id.equals(ItemRenderer.ITEM_ENCHANTMENT_GLINT)?current.texture:id;
    }
    public static void capture(Object state){if(current!=null)QUEUED.put(state,current);}
    public static Effect take(Object state){return QUEUED.remove(state);}
    public static void scoped(Effect effect,Runnable action){
        Effect previous=current;
        current=effect;
        try{action.run();}finally{current=previous;}
    }
    public static void clearQueued(){QUEUED.clear();}
    public void drawSword(DrawContext context,Effect effect,boolean hand,int x,int y,int size){
        var client=MinecraftClient.getInstance();
        var stack=Items.DIAMOND_SWORD.getDefaultStack();
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,true);
        var state=new ItemDisplayState(hand,false);
        var display=hand?ModelTransformationMode.FIRST_PERSON_RIGHT_HAND:ModelTransformationMode.GUI;
        PreviewItemModels.addItem(state,client.getItemRenderer(),stack,display,client);
        scoped(effect,()->PreviewGui.submit(context,"TextureMixer enchanted diamond sword",state,
                x+size*.25f,y+size*.25f,size*.5f,new ScreenRect(x,y,size,size)));
    }

    public void draw(DrawContext context,Effect effect,int x,int y,int size) {
        var world=MinecraftClient.getInstance().world;
        if(world==null)return;
        if(stand==null||!NativeCompat.inWorld(stand,world)){
            stand=EntityType.ARMOR_STAND.create(world);
            if(stand==null)return;
            stand.setSilent(true);
            stand.setNoGravity(true);
            equip(EquipmentSlot.HEAD,Items.IRON_HELMET);
            equip(EquipmentSlot.CHEST,Items.IRON_CHESTPLATE);
            equip(EquipmentSlot.LEGS,Items.IRON_LEGGINGS);
            equip(EquipmentSlot.FEET,Items.IRON_BOOTS);
        }
        scoped(effect,()->InventoryScreen.drawEntity(context,x+2,y+2,x+size-2,y+size-2,
                (int)(size*.45f),0,x+size*.84f,y+size*.44f,stand));
    }

    private void equip(EquipmentSlot slot,Item item){
        var stack=item.getDefaultStack();
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,true);
        stand.equipStack(slot,stack);
    }
    public void clear(){stand=null;}
}
