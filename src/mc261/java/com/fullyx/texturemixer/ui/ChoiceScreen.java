package com.fullyx.texturemixer.ui;

import com.fullyx.texturemixer.client.NativeCompat;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class ChoiceScreen extends MixerBaseScreen {
    public record Choice(String label,Runnable action){}
    private final Screen parent;private final List<Choice> choices;private UiDraw.Window window;
    public ChoiceScreen(Screen parent,String message,Choice...choices){super(Component.literal(message));this.parent=parent;this.choices=List.of(choices);}
    protected void init(){
        int w=Math.min(310,width-20);
        var optionWidths=choices.stream().map(choice->Math.max(46,font.width(choice.label())+14)).toList();
        int combined=optionWidths.stream().mapToInt(Integer::intValue).sum()+5*(choices.size()-1);
        boolean inline=combined<=w-24;
        int actionHeight=inline?UiDraw.CONTROL:choices.size()*22-4;
        int messageHeight=font.split(title,w-24).size()*10;
        window=UiDraw.Window.centered(width,height,w,12+messageHeight+10+actionHeight+12);
        int right=window.right()-12, startY=window.bottom()-12-actionHeight;
        for(int i=0;i<choices.size();i++){
            Choice choice=choices.get(i);
            int buttonWidth=inline?optionWidths.get(i):w-24;
            var button=addRenderableWidget(new FlatButton(inline?right-buttonWidth:window.x()+12,startY+(inline?0:i*22),
                    buttonWidth,UiDraw.CONTROL,Component.literal(choice.label()),b->choice.action().run()));
            if(i==0)button.primary();
            if(i==choices.size()-1)setInitialFocus(button);
            right-=buttonWidth+5;
        }
    }
    public void extractRenderState(GuiGraphicsExtractor ctx,int mx,int my,float d){
        window.draw(ctx);
        int y=window.y()+12;
        for(var line:font.split(title,window.width()-24)){
            ctx.text(font,line,window.x()+12,y,UiDraw.TEXT,false);
            y+=10;
        }
        super.extractRenderState(ctx,mx,my,d);
    }
    public boolean isPauseScreen(){return false;}
    public void onClose(){NativeCompat.setScreen(minecraft, parent);}
}
