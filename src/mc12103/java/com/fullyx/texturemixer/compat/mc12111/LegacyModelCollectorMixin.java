package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.render.model.ReferencedModelsCollector;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ReferencedModelsCollector.class)
public abstract class LegacyModelCollectorMixin {
    @Shadow @Final private Map<Identifier,UnbakedModel> inputs;

    @ModifyExpressionValue(method="addBlockStates",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/model/ReferencedModelsCollector;getRequiredModels()Ljava/util/Set;"))
    private Set<ModelIdentifier> texturemixer$previewModels(Set<ModelIdentifier> models){
        if(RenderScope.currentArena()!=null)models.removeIf(id->!inputs.containsKey(id.id().withPrefixedPath("item/")));
        return models;
    }
}
