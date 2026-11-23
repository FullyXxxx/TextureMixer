package com.fullyx.texturemixer.ui;

import static com.fullyx.texturemixer.core.UiLanguage.tr;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
public final class SkyConditionsScreen extends MixerBaseScreen {
    private final MixerScreen parent;private UiDraw.Window window;
    public SkyConditionsScreen(MixerScreen parent){super(Text.literal(tr("Sky 预览条件")));this.parent=parent;}
    protected void init(){
        window=UiDraw.Window.centered(width,height,314,151);
        int x=window.x(),y=window.y(),w=window.width();
        int half=(w-29)/2;
        addDrawableChild(new FlatButton(x+12,y+53,half,UiDraw.CONTROL,Text.literal(parent.timeName()),b->{parent.cycleSkyTime();clearAndInit();}));
        addDrawableChild(new FlatButton(x+17+half,y+53,half,UiDraw.CONTROL,Text.literal(parent.weatherName()),b->{parent.cycleSkyWeather();clearAndInit();}));
        addDrawableChild(new FlatButton(x+12,y+77,w-24,UiDraw.CONTROL,Text.literal(tr("匹配选中来源的首层条件")),b->{client.setScreen(parent);parent.matchSky();}).primary());
        addDrawableChild(new FlatButton(window.right()-68,window.bottom()-28,56,UiDraw.CONTROL,Text.literal(tr("返回")),b->close()));
    }
    public void render(DrawContext context,int mx,int my,float delta){
        window.draw(context);
        int x=window.x(),y=window.y();
        context.drawText(textRenderer,title,x+12,y+12,UiDraw.TEXT,false);
        var lines=textRenderer.wrapLines(Text.literal(parent.skySummary()),window.width()-24);
        for(int i=0;i<Math.min(2,lines.size());i++)context.drawText(textRenderer,lines.get(i),x+12,y+29+i*10,UiDraw.SECONDARY,false);
        context.drawText(textRenderer,tr("仅模拟预览；不会更改世界"),x+12,y+103,UiDraw.SECONDARY,false);
        super.render(context,mx,my,delta);
    }
    public boolean shouldPause(){return false;}public void close(){client.setScreen(parent);}
}
