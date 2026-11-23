package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.core.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.texture.*;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import java.util.*;
import java.io.*;

public final class EquipmentBindings {
    private record Preview(String prefix,List<Identifier> textures,Map<String,LivingEntity> entities,java.util.function.Function<net.minecraft.client.util.SpriteIdentifier,Sprite> atlases){}
    private static final Map<String,Preview> PREVIEWS=new java.util.concurrent.ConcurrentHashMap<>();
    public static final class PreparedPreview implements AutoCloseable {
        private final Map<Identifier,NativeImage> images=new LinkedHashMap<>();
        @Override public void close(){images.values().forEach(NativeImage::close);images.clear();}
    }
    public static boolean hasPreviewTexture(Identifier id){return PREVIEWS.values().stream().anyMatch(p->p.textures.contains(id));}
    public static void registerPreview(String token,String prefix,Map<AssetId,byte[]> files,java.util.function.Function<net.minecraft.client.util.SpriteIdentifier,Sprite> atlases){
        registerPreview(token,prefix,preparePreview(files),atlases);
    }
    public static PreparedPreview preparePreview(Map<AssetId,byte[]> files){
        PreparedPreview prepared=new PreparedPreview();
        try {
        files.forEach((id,bytes)->{
            try{
                if((id.path().startsWith("textures/entity/")||id.path().startsWith("textures/models/armor/"))&&id.path().endsWith(".png")){
                    Identifier texture=new Identifier(id.namespace(),id.path());
                    prepared.images.put(texture,NativeImage.read(bytes));
                }
            }catch(Exception error){throw new IllegalArgumentException("Invalid preview equipment: "+id,error);}
        });
        return prepared;
        }catch(RuntimeException error){prepared.close();throw error;}
    }
    public static void registerPreview(String token,String prefix,PreparedPreview prepared,java.util.function.Function<net.minecraft.client.util.SpriteIdentifier,Sprite> atlases){
        List<Identifier> textures=new ArrayList<>();
        try {
            var pending=prepared.images.entrySet().iterator();
            while(pending.hasNext()){
                var entry=pending.next();Identifier id=entry.getKey();NativeImage image=entry.getValue();pending.remove();
                NativeImageBackedTexture texture=null;
                try{
                    texture=VersionedTexture.create("TextureMixer preview",image);
                    MinecraftClient.getInstance().getTextureManager().registerTexture(id,texture);
                    textures.add(id);
                }catch(RuntimeException error){if(texture!=null)texture.close();else image.close();throw error;}
            }
            PREVIEWS.put(token,new Preview(prefix,textures,new HashMap<>(),atlases));
        }catch(RuntimeException error){
            textures.forEach(MinecraftClient.getInstance().getTextureManager()::destroyTexture);
            throw error;
        }finally{prepared.close();}
    }
    public static String prefix(ItemStack stack){
        String token=arena(stack);
        if(token!=null)return PREVIEWS.get(token).prefix;
        MixerService service=TextureMixerClient.SERVICE;
        return service==null?null:service.prefixForItem(Registries.ITEM.getId(stack.getItem()).toString());
    }
    public static String arena(ItemStack stack){
        var data=stack.getNbt();if(data==null)return null;
        String token=data.getString("texturemixer_preview");return PREVIEWS.containsKey(token)?token:null;
    }
    public static void drawWorn(PreviewCache.Arena arena,DrawContext context,Catalog.Target target,int x,int y,int size){
        Preview preview=PREVIEWS.get(arena.token);if(preview==null)return;
        var client=MinecraftClient.getInstance();if(client.world==null)return;
        LivingEntity mannequin=preview.entities.computeIfAbsent(target.id(),ignored->{
            String carrier="armor_stand";
            if(target.id().contains("horse_armor"))carrier="horse";
            else if(target.id().contains("wolf_armor"))carrier="wolf";
            else if(target.id().endsWith("_carpet"))carrier="llama";
            else if(target.id().contains("nautilus_armor"))carrier="nautilus";
            else if(target.id().endsWith("_harness"))carrier="happy_ghast";
            else if(target.id().equals("minecraft:saddle"))carrier="pig";
            var type=Registries.ENTITY_TYPE.get(new Identifier("minecraft",carrier));
            Entity entity=type.create(client.world);
            if(!(entity instanceof LivingEntity living))return null;
            ItemStack copy=target.stack().copy();copy.getOrCreateNbt().putString("texturemixer_preview",arena.token);
            EquipmentSlot slot=copy.getItem() instanceof net.minecraft.item.ArmorItem armor?armor.getSlotType()
                    :copy.isOf(net.minecraft.item.Items.ELYTRA)?EquipmentSlot.CHEST:EquipmentSlot.HEAD;
            living.equipStack(slot,copy);
            living.setSilent(true);living.setNoGravity(true);return living;
        });
        if(mannequin==null)return;
        int scale=(int)(size/(Math.max(1.5f,mannequin.getHeight())+0.4f));
        float turn=target.id().equals("minecraft:elytra")?x+size*6f:x+size*.5f;
        InventoryScreen.drawEntity(context,x+3,y+3,x+size-3,y+size-3,scale,0,turn,y+size*.45f,mannequin);
    }
    public static void unregisterPreview(String token){
        Preview preview=PREVIEWS.remove(token);if(preview==null)return;
        for(Identifier texture:preview.textures)MinecraftClient.getInstance().getTextureManager().destroyTexture(texture);
    }
    public static void clearWorld(){PREVIEWS.values().forEach(preview->preview.entities.clear());}
    public static long previewTextureBytes(String token){
        Preview preview=PREVIEWS.get(token);if(preview==null)return 0;long bytes=0;
        var textures=((com.fullyx.texturemixer.compat.mc12111.TextureManagerAccess)MinecraftClient.getInstance().getTextureManager()).texturemixer$textures();
        for(Identifier id:preview.textures)bytes+=VersionedTexture.bytes(textures.get(id));
        return bytes;
    }
}
