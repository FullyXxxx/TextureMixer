package com.fullyx.texturemixer.ui;

import com.fullyx.texturemixer.client.NativeCompat;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
public final class SkyConditionsScreen extends MixerBaseScreen {
    private final MixerScreen parent;private UiDraw.Window window;
    public SkyConditionsScreen(MixerScreen parent){super(Component.literal(tr("Sky 预览条件")));this.parent=parent;}
    protected void init(){
        window=UiDraw.Window.centered(width,height,314,151);
        int x=window.x(),y=window.y(),w=window.width();
        int half=(w-29)/2;
        addRenderableWidget(new FlatButton(x+12,y+53,half,UiDraw.CONTROL,Component.literal(parent.timeName()),b->{parent.cycleSkyTime();rebuildWidgets();}));
        addRenderableWidget(new FlatButton(x+17+half,y+53,half,UiDraw.CONTROL,Component.literal(parent.weatherName()),b->{parent.cycleSkyWeather();rebuildWidgets();}));
        addRenderableWidget(new FlatButton(x+12,y+77,w-24,UiDraw.CONTROL,Component.literal(tr("匹配选中来源的首层条件")),b->{NativeCompat.setScreen(minecraft, parent);parent.matchSky();}).primary());
        addRenderableWidget(new FlatButton(window.right()-68,window.bottom()-28,56,UiDraw.CONTROL,Component.literal(tr("返回")),b->onClose()));
    }
    public void extractRenderState(GuiGraphicsExtractor context,int mx,int my,float delta){
        window.draw(context);
        int x=window.x(),y=window.y();
        context.text(font,title,x+12,y+12,UiDraw.TEXT,false);
        var lines=font.split(Component.literal(parent.skySummary()),window.width()-24);
        for(int i=0;i<Math.min(2,lines.size());i++)context.text(font,lines.get(i),x+12,y+29+i*10,UiDraw.SECONDARY,false);
        context.text(font,tr("仅模拟预览；不会更改世界"),x+12,y+103,UiDraw.SECONDARY,false);
        super.extractRenderState(context,mx,my,delta);
    }
    public boolean isPauseScreen(){return false;}public void onClose(){NativeCompat.setScreen(minecraft, parent);}
}
