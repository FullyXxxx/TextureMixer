package com.fullyx.texturemixer.compat;
import com.fullyx.texturemixer.TextureMixerClient;
import com.terraformersmc.modmenu.api.*;
public final class ModMenuIntegration implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory(){return TextureMixerClient::configurationScreen;}
}
