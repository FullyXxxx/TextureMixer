package com.fullyx.texturemixer.ui;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.client.MixerService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class ModSettingsScreen extends MixerBaseScreen {
    private final Screen parent;
    private final MixerService service;
    private FlatButton toggle,open,back;
    private UiDraw.Window window;
    private boolean pending;
    private String notice="";
    public ModSettingsScreen(Screen parent,MixerService service){super(Text.literal("TextureMixer"));this.parent=parent;this.service=service;}
    @Override protected void init(){
        window=UiDraw.Window.centered(width,height,280,132);
        int w=window.width()-24,x=window.x()+12,y=window.y()+47;
        toggle=addDrawableChild(new FlatButton(x,y,w,UiDraw.CONTROL,Text.empty(),button->{
            if(pending||service.busy)return;
            pending=true;notice=tr("正在切换资源…");
            service.setModEnabled(!service.modEnabled()).whenCompleteAsync((ignored,error)->{
                pending=false;notice=error==null?service.status:MixerService.message(error);updateControls();
            },client);
            updateControls();
        }));
        open=addDrawableChild(new FlatButton(x,y+24,w,UiDraw.CONTROL,Text.literal(tr("打开资源混搭")),button->client.setScreen(new MixerScreen(service,()->client.setScreen(this)))).primary());
        back=addDrawableChild(new FlatButton(window.right()-68,window.bottom()-28,56,UiDraw.CONTROL,Text.literal(tr("完成")),button->close()));
        updateControls();
    }
    private void updateControls(){
        if(toggle==null)return;
        toggle.setMessage(Text.literal("TextureMixer "+(service.modEnabled()?"ON":"OFF")));
        toggle.active=service.catalog!=null&&!pending&&!service.busy;
        open.active=toggle.active&&service.modEnabled();back.active=!pending;
    }
    @Override public void tick(){updateControls();}
    @Override public void render(DrawContext context,int mx,int my,float delta){
        window.draw(context);
        context.drawText(textRenderer,title,window.x()+12,window.y()+12,UiDraw.TEXT,false);
        context.drawText(textRenderer,tr("控制资源包界面与已应用的混搭"),window.x()+12,window.y()+28,UiDraw.SECONDARY,false);
        super.render(context,mx,my,delta);
        if(!notice.isBlank())context.drawText(textRenderer,UiDraw.ellipsis(textRenderer,notice,window.width()-85),window.x()+12,back.getY()+5,UiDraw.SECONDARY,false);
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){if(!pending)client.setScreen(parent);}
}
