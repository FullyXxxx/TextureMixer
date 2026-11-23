package com.fullyx.texturemixer.ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import java.util.List;
public final class ChoiceScreen extends MixerBaseScreen {
    public record Choice(String label,Runnable action){}
    private final Screen parent;private final List<Choice> choices;private UiDraw.Window window;
    public ChoiceScreen(Screen parent,String message,Choice...choices){super(Text.literal(message));this.parent=parent;this.choices=List.of(choices);}
    protected void init(){
        int w=Math.min(310,width-20);
        var optionWidths=choices.stream().map(choice->Math.max(46,textRenderer.getWidth(choice.label())+14)).toList();
        int combined=optionWidths.stream().mapToInt(Integer::intValue).sum()+5*(choices.size()-1);
        boolean inline=combined<=w-24;
        int actionHeight=inline?UiDraw.CONTROL:choices.size()*22-4;
        int messageHeight=textRenderer.wrapLines(title,w-24).size()*10;
        window=UiDraw.Window.centered(width,height,w,12+messageHeight+10+actionHeight+12);
        int right=window.right()-12, startY=window.bottom()-12-actionHeight;
        for(int i=0;i<choices.size();i++){
            Choice choice=choices.get(i);
            int buttonWidth=inline?optionWidths.get(i):w-24;
            var button=addDrawableChild(new FlatButton(inline?right-buttonWidth:window.x()+12,startY+(inline?0:i*22),
                    buttonWidth,UiDraw.CONTROL,Text.literal(choice.label()),b->choice.action().run()));
            if(i==0)button.primary();
            if(i==choices.size()-1)setInitialFocus(button);
            right-=buttonWidth+5;
        }
    }
    public void render(DrawContext ctx,int mx,int my,float d){
        window.draw(ctx);
        int y=window.y()+12;
        for(var line:textRenderer.wrapLines(title,window.width()-24)){
            ctx.drawText(textRenderer,line,window.x()+12,y,UiDraw.TEXT,false);
            y+=10;
        }
        super.render(ctx,mx,my,d);
    }
    public boolean shouldPause(){return false;}
    public void close(){client.setScreen(parent);}
}
