package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModelLoader.class)
public abstract class LegacyModelCollectorMixin {
    @ModifyExpressionValue(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraft/registry/DefaultedRegistry;getIds()Ljava/util/Set;"))
    private Set<Identifier> texturemixer$previewOnly(Set<Identifier> ids,@Local(argsOnly=true,ordinal=0) Map<Identifier,JsonUnbakedModel> models){
        if(RenderScope.currentArena()==null)return ids;
        return ids.stream().filter(id->models.containsKey(id.withPath(path->"models/item/"+path+".json"))).collect(Collectors.toSet());
    }
}
