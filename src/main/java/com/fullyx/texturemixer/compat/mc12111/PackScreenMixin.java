package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.PackScreenContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.text.Text;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PackScreen.class)
public abstract class PackScreenMixin implements PackScreenContext {
    @Unique private boolean texturemixer$clientResources;
    @Inject(method="<init>",at=@At("RETURN"))
    private void texturemixer$captureRepository(ResourcePackManager manager,Consumer<ResourcePackManager> output,Path directory,Text title,CallbackInfo callback){
        texturemixer$clientResources=manager==MinecraftClient.getInstance().getResourcePackManager();
    }
    @Override public boolean texturemixer$isClientResources(){return texturemixer$clientResources;}
}
