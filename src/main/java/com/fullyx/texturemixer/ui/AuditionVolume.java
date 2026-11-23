package com.fullyx.texturemixer.ui;

import static com.fullyx.texturemixer.core.UiLanguage.tr;
import com.fullyx.texturemixer.client.AudioPreview;
import com.fullyx.texturemixer.client.UiFonts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

final class AuditionVolume extends SliderWidget {
    private final AudioPreview audio;

    AuditionVolume(int x,int y,int width,AudioPreview audio){
        super(x,y,width,UiDraw.CONTROL,Text.empty(),audio.volume());
        this.audio=audio;
        updateMessage();
    }
    @Override protected void updateMessage(){
        setMessage(Text.literal(tr("试听音量")+" "+Math.round(value*100)+"%"));
    }
    @Override protected void applyValue(){audio.setVolume(value);}
    @Override public void renderWidget(DrawContext context,int mx,int my,float delta){
        value=audio.volume();
        updateMessage();
        int x=getX(), y=getY(), width=getWidth();
        UiDraw.frame(context,x,y,width,getHeight(),4,0xe4474747,isFocused()?UiDraw.FOCUS:0xff575757);
        int end=x+4+(int)Math.round(value*(width-8));
        int trackY=y+getHeight()-4;
        context.fill(x+4,trackY,x+width-4,trackY+1,0xff717171);
        context.fill(x+4,trackY,end,trackY+1,UiDraw.TEXT);
        context.fill(end-1,trackY-2,end+1,trackY+2,UiDraw.TEXT);
        var font=UiFonts.renderer();
        String label=UiDraw.ellipsis(font,getMessage().getString(),width-8);
        context.drawText(font,label,x+(width-font.getWidth(label))/2,y+2,UiDraw.TEXT,false);
    }
}
