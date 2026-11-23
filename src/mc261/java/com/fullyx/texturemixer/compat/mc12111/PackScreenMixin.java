package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.PackScreenContext;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PackSelectionScreen.class)
public abstract class PackScreenMixin implements PackScreenContext {
    @Unique private boolean texturemixer$clientResources;
    @Inject(method="<init>",at=@At("RETURN"))
    private void texturemixer$captureRepository(PackRepository manager,Consumer<PackRepository> output,Path directory,Component title,CallbackInfo callback){
        texturemixer$clientResources=manager==Minecraft.getInstance().getResourcePackRepository();
    }
    @Override public boolean texturemixer$isClientResources(){return texturemixer$clientResources;}
}
