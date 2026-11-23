package com.fullyx.texturemixer.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Native glint layers with a private sampler binding for each preview source. */
public final class EnchantPreview {
    private static final Map<Object,Effect> QUEUED=new IdentityHashMap<>();
    private static Effect current;
    private ArmorStand stand;


    public static final class Effect {
        private final Identifier texture;
        private final boolean armor;
        private RenderType armorLayer;

        public Effect(Identifier texture,boolean armor){this.texture=texture;this.armor=armor;}
        public boolean armor(){return armor;}
    }
    public static RenderType armorLayer(RenderType original){
        if(current==null||!current.armor)return original;
        if(current.armorLayer==null){
            current.armorLayer=NativeCompat.armorGlint(original,current.texture);
        }
        return current.armorLayer;
    }
    public static Identifier itemTexture(Identifier id){
        if(current==null)return id;
        return current.armor?NativeCompat.armorGlintTexture(id,current.texture)
                :id.equals(Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png"))?current.texture:id;
    }
    public static void capture(Object state){if(current!=null)QUEUED.put(state,current);}
    public static Effect take(Object state){return QUEUED.remove(state);}
    public static void scoped(Effect effect,Runnable action){
        inScope(effect, () -> { action.run(); return null; });
    }
    public static <T> T inScope(Effect effect, java.util.function.Supplier<T> action) {
        Effect previous=current;
        current=effect;
        try{return action.get();}finally{current=previous;}
    }
    public static void clearQueued(){QUEUED.clear();}
    private static final class GlintItemState extends ItemDisplayState {
        private final Effect effect;
        GlintItemState(Effect effect,boolean hand){super(hand,false);this.effect=effect;}
    }
    public static Effect effect(ItemStackRenderState state){
        return state instanceof GlintItemState preview?preview.effect:null;
    }
    public void drawSword(GuiGraphicsExtractor context,Effect effect,boolean hand,int x,int y,int size){
        var client=Minecraft.getInstance();
        var stack=com.fullyx.texturemixer.TextureMixerClient.SERVICE.catalog.defaultStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);
        var state=new GlintItemState(effect,hand);
        var display=PreviewItemModels.update(client.getItemModelResolver(),state,stack,hand,client);
        state.appendModelIdentityElement(effect);
        state.appendModelIdentityElement(display);
        state.setAnimated();
        scoped(effect,()->PreviewGui.submit(context,"TextureMixer enchanted diamond sword",state,
                x+size*.25f,y+size*.25f,size*.5f,new ScreenRectangle(x,y,size,size)));
    }

    public void draw(GuiGraphicsExtractor context,Effect effect,int x,int y,int size) {
        var world=Minecraft.getInstance().level;
        if(world==null)return;
        if(stand==null||!NativeCompat.inWorld(stand,world)){
            stand=net.minecraft.world.entity.EntityTypes.ARMOR_STAND.create(world,EntitySpawnReason.COMMAND);
            if(stand==null)return;
            EquipmentBindings.assignPreviewId(stand);
            stand.setSilent(true);
            stand.setNoGravity(true);
            equip(EquipmentSlot.HEAD,Items.IRON_HELMET);
            equip(EquipmentSlot.CHEST,Items.IRON_CHESTPLATE);
            equip(EquipmentSlot.LEGS,Items.IRON_LEGGINGS);
            equip(EquipmentSlot.FEET,Items.IRON_BOOTS);
        }
        scoped(effect,()->InventoryScreen.extractEntityInInventoryFollowsMouse(context,x+2,y+2,x+size-2,y+size-2,
                (int)(size*.45f),0,x+size*.84f,y+size*.44f,stand));
    }

    private void equip(EquipmentSlot slot,Item item){
        var stack=com.fullyx.texturemixer.TextureMixerClient.SERVICE.catalog.defaultStack(item);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);
        stand.setItemSlot(slot,stack);
    }
    public void clear(){stand=null;}
}
