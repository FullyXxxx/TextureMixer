package com.fullyx.texturemixer.ui;

import com.fullyx.texturemixer.client.NativeCompat;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.client.MixerService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ModSettingsScreen extends MixerBaseScreen {
    private final Screen parent;
    private final MixerService service;
    private FlatButton toggle,open,back;
    private UiDraw.Window window;
    private boolean pending;
    private String notice="";
    public ModSettingsScreen(Screen parent,MixerService service){super(Component.literal("TextureMixer"));this.parent=parent;this.service=service;}
    @Override protected void init(){
        window=UiDraw.Window.centered(width,height,280,132);
        int w=window.width()-24,x=window.x()+12,y=window.y()+47;
        toggle=addRenderableWidget(new FlatButton(x,y,w,UiDraw.CONTROL,Component.empty(),button->{
            if(pending||service.busy)return;
            pending=true;notice=tr("正在切换资源…");
            service.setModEnabled(!service.modEnabled()).whenCompleteAsync((ignored,error)->{
                pending=false;notice=error==null?service.status:MixerService.message(error);updateControls();
            },minecraft);
            updateControls();
        }));
        open=addRenderableWidget(new FlatButton(x,y+24,w,UiDraw.CONTROL,Component.literal(tr("打开资源混搭")),button->NativeCompat.setScreen(minecraft, new MixerScreen(service,()->NativeCompat.setScreen(minecraft, this)))).primary());
        back=addRenderableWidget(new FlatButton(window.right()-68,window.bottom()-28,56,UiDraw.CONTROL,Component.literal(tr("完成")),button->onClose()));
        updateControls();
    }
    private void updateControls(){
        if(toggle==null)return;
        toggle.setMessage(Component.literal("TextureMixer "+(service.modEnabled()?"ON":"OFF")));
        toggle.active=service.catalog!=null&&!pending&&!service.busy;
        open.active=toggle.active&&service.modEnabled();back.active=!pending;
    }
    @Override public void tick(){updateControls();}
    @Override public void extractRenderState(GuiGraphicsExtractor context,int mx,int my,float delta){
        window.draw(context);
        context.text(font,title,window.x()+12,window.y()+12,UiDraw.TEXT,false);
        context.text(font,tr("控制资源包界面与已应用的混搭"),window.x()+12,window.y()+28,UiDraw.SECONDARY,false);
        super.extractRenderState(context,mx,my,delta);
        if(!notice.isBlank())context.text(font,UiDraw.ellipsis(font,notice,window.width()-85),window.x()+12,back.getY()+5,UiDraw.SECONDARY,false);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){if(!pending)NativeCompat.setScreen(minecraft, parent);}
}
