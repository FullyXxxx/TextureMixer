package com.fullyx.texturemixer;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import net.fabricmc.api.ClientModInitializer;
import com.fullyx.texturemixer.client.MixerService;
import com.fullyx.texturemixer.client.PackScreenContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import com.fullyx.texturemixer.ui.ModSettingsScreen;
import com.fullyx.texturemixer.ui.MessageScreen;
import com.fullyx.texturemixer.ui.MixerScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TextureMixerClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("TextureMixer");
    public static MixerService SERVICE;
    @Override public void onInitializeClient() {
        LOG.info("TextureMixer initializing");
        com.fullyx.texturemixer.sky.SkySceneRenderer.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(SERVICE==null){
                try{service(client);}catch(Exception error){LOG.error("Initialization failed",error);return;}
            }
            SERVICE.tick();
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(literal("texturemixer").executes(ctx->{open(MinecraftClient.getInstance());return 1;})
            .then(literal("reload").executes(ctx->{
                if(SERVICE==null||SERVICE.catalog==null){
                    ctx.getSource().sendFeedback(Text.literal(tr("TextureMixer 正在准备资源，请稍候")));
                    return 0;
                }
                SERVICE.refreshSources().exceptionally(error->{
                    MinecraftClient.getInstance().execute(()->ctx.getSource().sendFeedback(Text.literal(MixerService.message(error))));
                    return null;
                });
                ctx.getSource().sendFeedback(Text.literal(tr("TextureMixer 正在刷新手动添加的来源")));
                return 1;
            }))));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->{if(SERVICE!=null)SERVICE.close();});
    }
    private static MixerService service(MinecraftClient client)throws java.io.IOException {
        if(SERVICE==null){
            MixerService created=new MixerService(client);
            try{created.initialize();}
            catch(RuntimeException error){created.close();throw error;}
            SERVICE=created;
        }
        return SERVICE;
    }
    public static Screen resourcePackScreen(Screen requested){
        boolean vanilla=requested instanceof PackScreen;
        boolean packed=requested!=null&&requested.getClass().getName().equals("io.github.fishstiz.packed_packs.gui.screens.PackedPacksScreen");
        if(!vanilla&&!packed)return requested;
        var client=MinecraftClient.getInstance();
        try {
            var mixer=service(client);if(!mixer.modEnabled())return requested;
            if(vanilla){
                if(!((PackScreenContext)requested).texturemixer$isClientResources())return requested;
                return new MixerScreen(mixer,requested::close);
            }
            // Packed Packs replaces PackScreen before its init. Its record and parent fields
            // retain stable Java names, and repository identity excludes data-pack screens.
            var originalField=requested.getClass().getDeclaredField("original");originalField.setAccessible(true);
            Object original=originalField.get(requested);
            if(original.getClass().getMethod("repository").invoke(original)!=client.getResourcePackManager())return requested;
            var previousField=requested.getClass().getDeclaredField("previous");previousField.setAccessible(true);
            Screen parent=(Screen)previousField.get(requested);
            return new MixerScreen(mixer,()->client.setScreen(parent));
        }catch(Exception error){LOG.error("Could not integrate resource pack screen",error);return requested;}
    }
    public static Screen configurationScreen(Screen parent){
        try{return new ModSettingsScreen(parent,service(MinecraftClient.getInstance()));}
        catch(Exception error){return new MessageScreen(parent,"TextureMixer",MixerService.message(error));}
    }
    public static void open(MinecraftClient client){
        if(SERVICE==null||SERVICE.catalog==null){if(client.player!=null)client.player.sendMessage(Text.literal(tr("TextureMixer 正在准备资源，请稍候")),false);return;}
        if(!SERVICE.modEnabled()){client.setScreen(configurationScreen(client.currentScreen));return;}
        SERVICE.checkSources();
        var parent=client.currentScreen;
        client.setScreen(new MixerScreen(SERVICE,()->client.setScreen(parent)));
    }
}
