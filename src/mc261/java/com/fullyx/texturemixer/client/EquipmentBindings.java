package com.fullyx.texturemixer.client;

import net.minecraft.client.resources.model.sprite.SpriteId;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.core.*;
import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import java.io.*;

public final class EquipmentBindings {
    private record Preview(String prefix,Map<Identifier,EquipmentClientInfo> models,List<Identifier> textures,Map<String,LivingEntity> entities,java.util.function.Function<net.minecraft.client.resources.model.sprite.SpriteId,TextureAtlasSprite> atlases,ItemModelResolver items){}
    private static final Map<String,Preview> PREVIEWS=new java.util.concurrent.ConcurrentHashMap<>();
    public static final class PreparedPreview implements AutoCloseable {
        private final Map<Identifier,EquipmentClientInfo> models=new HashMap<>();
        private final Map<Identifier,NativeImage> images=new LinkedHashMap<>();
        @Override public void close(){images.values().forEach(NativeImage::close);images.clear();}
    }
    public static boolean hasPreviewTexture(Identifier id){return PREVIEWS.values().stream().anyMatch(p->p.textures.contains(id));}
    public static void registerPreview(String token,String prefix,Map<AssetId,byte[]> files,java.util.function.Function<net.minecraft.client.resources.model.sprite.SpriteId,TextureAtlasSprite> atlases,ItemModelResolver items){
        registerPreview(token,prefix,preparePreview(files),atlases,items);
    }
    public static PreparedPreview preparePreview(Map<AssetId,byte[]> files){
        PreparedPreview prepared=new PreparedPreview();
        try {
        files.forEach((id,bytes)->{
            try{
                if(id.path().startsWith("equipment/")&&id.path().endsWith(".json")){
                    String path=id.path().substring(10,id.path().length()-5);
                    EquipmentClientInfo model=EquipmentClientInfo.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8))).getOrThrow();
                    prepared.models.put(Identifier.fromNamespaceAndPath(id.namespace(),path),model);
                }
                if(id.path().startsWith("textures/entity/")&&id.path().endsWith(".png")){
                    Identifier texture=Identifier.fromNamespaceAndPath(id.namespace(),id.path());
                    prepared.images.put(texture,NativeImage.read(bytes));
                }
            }catch(Exception error){throw new IllegalArgumentException("Invalid preview equipment: "+id,error);}
        });
        return prepared;
        }catch(RuntimeException error){prepared.close();throw error;}
    }
    public static void registerPreview(String token,String prefix,PreparedPreview prepared,java.util.function.Function<net.minecraft.client.resources.model.sprite.SpriteId,TextureAtlasSprite> atlases,ItemModelResolver items){
        List<Identifier> textures=new ArrayList<>();
        try {
            var pending=prepared.images.entrySet().iterator();
            while(pending.hasNext()){
                var entry=pending.next();Identifier id=entry.getKey();NativeImage image=entry.getValue();pending.remove();
                DynamicTexture texture=null;
                try{
                    texture=VersionedTexture.create("TextureMixer preview",image);
                    Minecraft.getInstance().getTextureManager().register(id,texture);
                    textures.add(id);
                }catch(RuntimeException error){if(texture!=null)texture.close();else image.close();throw error;}
            }
            PREVIEWS.put(token,new Preview(prefix,prepared.models,textures,new HashMap<>(),atlases,items));
        }catch(RuntimeException error){
            textures.forEach(Minecraft.getInstance().getTextureManager()::release);
            throw error;
        }finally{prepared.close();}
    }
    public static EquipmentClientInfo previewModel(ResourceKey<EquipmentAsset> key){
        for(Preview preview:PREVIEWS.values()){EquipmentClientInfo model=preview.models.get(key.identifier());if(model!=null)return model;}return null;
    }
    public static String prefix(ItemStack stack){
        String token=arena(stack);if(token!=null)return PREVIEWS.get(token).prefix;
        MixerService service=TextureMixerClient.SERVICE;
        if (service == null || service.catalog == null) return null;
        Identifier model=stack.get(DataComponents.ITEM_MODEL);
        if(!Objects.equals(model,service.catalog.defaultStack(stack.getItem()).get(DataComponents.ITEM_MODEL)))return null;
        return service.prefixForItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
    public static ResourceKey<EquipmentAsset> key(ResourceKey<EquipmentAsset> original,ItemStack stack){
        String prefix=prefix(stack);if(prefix==null)return original;
        Identifier id=original.identifier(),mapped=Identifier.fromNamespaceAndPath(prefix+id.getNamespace(),id.getPath());String token=arena(stack);
        if(token!=null&&!PREVIEWS.get(token).models.containsKey(mapped))return original;
        if(token==null){var service=TextureMixerClient.SERVICE;var generation=service==null?null:service.runtime();
            if(generation==null||!generation.equipment().containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem())+"|"+id))return original;}
        return ResourceKey.create(EquipmentAssets.ROOT_ID,mapped);
    }
    public static String arena(ItemStack stack){
        var data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return null;
        String token=PreviewItemModels.readArena(data.copyTag());return PREVIEWS.containsKey(token)?token:null;
    }
    public static ItemModelResolver previewItems(ItemStack stack){String token=arena(stack);return token==null?null:PREVIEWS.get(token).items;}
    public static net.minecraft.client.renderer.texture.TextureAtlasSprite previewTrim(ResourceKey<EquipmentAsset> asset,Identifier texture){
        for(Preview preview:PREVIEWS.values())if(preview.atlases!=null&&preview.models.containsKey(asset.identifier()))
            return preview.atlases.apply(new net.minecraft.client.resources.model.sprite.SpriteId(net.minecraft.client.renderer.Sheets.ARMOR_TRIMS_SHEET,texture));
        return null;
    }
    public static void drawWorn(PreviewCache.Arena arena,GuiGraphicsExtractor context,Catalog.Target target,int x,int y,int size){
        Preview preview=PREVIEWS.get(arena.token);if(preview==null)return;
        var client=Minecraft.getInstance();if(client.level==null)return;
        LivingEntity mannequin=preview.entities.computeIfAbsent(target.id(),ignored->{
            String carrier="armor_stand";
            if(target.id().contains("horse_armor"))carrier="horse";
            else if(target.id().contains("wolf_armor"))carrier="wolf";
            else if(target.id().endsWith("_carpet"))carrier="llama";
            else if(target.id().contains("nautilus_armor"))carrier="nautilus";
            else if(target.id().endsWith("_harness"))carrier="happy_ghast";
            else if(target.id().equals("minecraft:saddle"))carrier="pig";
            var type=BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(carrier));
            Entity entity=type.create(client.level,EntitySpawnReason.COMMAND);
            if(!(entity instanceof LivingEntity living))return null;
            ItemStack copy=target.stack().copy();CompoundTag marker=new CompoundTag();marker.putString("texturemixer_preview",arena.token);copy.set(DataComponents.CUSTOM_DATA,CustomData.of(marker));
            var eq=copy.get(DataComponents.EQUIPPABLE);
            if(eq!=null)living.setItemSlot(eq.slot(),copy);
            else living.setItemSlot(EquipmentSlot.HEAD,copy);
            living.setSilent(true);living.setNoGravity(true);return living;
        });
        if(mannequin==null)return;
        int scale=(int)(size/(Math.max(1.5f,mannequin.getBbHeight())+0.4f));
        float turn=target.id().equals("minecraft:elytra")?x+size*6f:x+size*.5f;
        InventoryScreen.extractEntityInInventoryFollowsMouse(context,x+3,y+3,x+size-3,y+size-3,scale,0,turn,y+size*.45f,mannequin);
    }
    public static void unregisterPreview(String token){
        Preview preview=PREVIEWS.remove(token);if(preview==null)return;
        for(Identifier texture:preview.textures)Minecraft.getInstance().getTextureManager().release(texture);
    }
    public static void clearWorld(){PREVIEWS.values().forEach(preview->preview.entities.clear());}
    public static long previewTextureBytes(String token){
        Preview preview=PREVIEWS.get(token);if(preview==null)return 0;long bytes=0;
        var textures=((com.fullyx.texturemixer.compat.mc12111.TextureManagerAccess)Minecraft.getInstance().getTextureManager()).texturemixer$textures();
        for(Identifier id:preview.textures)bytes+=VersionedTexture.bytes(textures.get(id));
        return bytes;
    }
}
