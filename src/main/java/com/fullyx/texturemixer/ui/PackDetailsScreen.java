package com.fullyx.texturemixer.ui;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.core.MixerConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

final class PackDetailsScreen extends MixerBaseScreen {
    private final MixerScreen mixer;
    private final Screen parent;
    private final String source;
    private PackIcons icons;
    private UiDraw.Window window;

    PackDetailsScreen(MixerScreen mixer,Screen parent,String source){
        super(Text.literal(mixer.sourceName(source)));
        this.mixer=mixer;this.parent=parent;this.source=source;
    }
    @Override protected void init(){
        if(icons!=null)icons.close();
        icons=new PackIcons(mixer.service);
        int categoryTop=68;
        int rows=(MixerConfig.Kind.values().length+2)/3;
        window=UiDraw.Window.centered(width,height,328,categoryTop+rows*23+30);
        int x=window.x(),y=window.y(),w=window.width();
        int buttonWidth=(w-34)/3;
        for(var kind:MixerConfig.Kind.values()){
            String label=tr(kind.label);
            addDrawableChild(new FlatButton(x+12+kind.ordinal()%3*(buttonWidth+5),y+categoryTop+kind.ordinal()/3*23,buttonWidth,UiDraw.CONTROL,
                    Text.literal(label),button->client.setScreen(MixerScreen.preview(mixer,source,kind,this))));
        }
        int actionY=window.bottom()-28;
        var setMain=addDrawableChild(new FlatButton(window.right()-77,actionY,65,UiDraw.CONTROL,Text.literal(tr("选择")),button->{
            mixer.previewMain(source,this);
        }).primary());
        var pack=mixer.service.sources.get(source);
        setMain.active=pack!=null&&!mixer.service.busy&&!mixer.libraryImporting();
        addDrawableChild(new FlatButton(window.right()-135,actionY,53,UiDraw.CONTROL,Text.literal(tr("返回")),button->close()));
    }
    @Override public void render(DrawContext context,int mx,int my,float delta){
        window.draw(context);
        int x=window.x(),y=window.y();
        icons.draw(context,source,x+12,y+12,34);
        int textX=x+54,textWidth=window.right()-12-textX;
        context.drawText(textRenderer,UiDraw.ellipsis(textRenderer,mixer.sourceName(source),textWidth),textX,y+13,UiDraw.TEXT,false);
        var record=mixer.draft.sources.get(source);
        if(record!=null){
            var lines=textRenderer.wrapLines(Text.literal(MixerScreen.plainName(record.canonicalRoot())),textWidth);
            for(int i=0;i<Math.min(2,lines.size());i++)context.drawText(textRenderer,lines.get(i),textX,y+27+i*10,UiDraw.SECONDARY,false);
        }
        String error=mixer.service.sourceErrors.get(source);
        if(error!=null)context.drawText(textRenderer,UiDraw.ellipsis(textRenderer,error,window.width()-24),x+12,y+54,0xffffff8a,false);
        super.render(context,mx,my,delta);
    }
    @Override public void removed(){if(icons!=null)icons.close();}
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){client.setScreen(parent);}
}
