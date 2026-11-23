package com.fullyx.texturemixer.ui;

import static com.fullyx.texturemixer.core.UiLanguage.tr;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.nio.file.Path;
import java.util.function.Consumer;
public final class PathScreen extends MixerBaseScreen {
    private final Screen parent;private final Consumer<Path> action;private TextFieldWidget path;private String error="";private UiDraw.Window window;private final boolean folder;
    public PathScreen(Screen parent,Consumer<Path> action){this(parent,false,action);}
    public PathScreen(Screen parent,boolean folder,Consumer<Path> action){super(Text.literal(folder?tr("添加文件夹"):tr("添加 ZIP 材质包")));this.parent=parent;this.folder=folder;this.action=action;}
    protected void init(){
        window=UiDraw.Window.centered(width,height,340,116);
        String previous=path==null?"":path.getText();
        path=addDrawableChild(new MixerField(textRenderer,window.x()+12,window.y()+47,window.width()-24,UiDraw.CONTROL,Text.literal(folder?tr("文件夹绝对路径"):tr("ZIP 文件绝对路径"))));
        path.setMaxLength(2048);path.setText(previous);setInitialFocus(path);
        addDrawableChild(new FlatButton(window.right()-121,window.bottom()-28,52,UiDraw.CONTROL,Text.literal(tr("取消")),b->close()));
        addDrawableChild(new FlatButton(window.right()-64,window.bottom()-28,52,UiDraw.CONTROL,Text.literal(tr("添加")),b->{
            try{Path p=Path.of(path.getText().trim());if(!p.isAbsolute())throw new IllegalArgumentException(tr("请输入绝对路径"));action.accept(p);}
            catch(Exception e){error=com.fullyx.texturemixer.client.MixerService.message(e);}
        }).primary());
    }
    public void render(DrawContext ctx,int mx,int my,float d){
        window.draw(ctx);
        int x=window.x(),y=window.y();
        ctx.drawText(textRenderer,title,x+12,y+12,UiDraw.TEXT,false);
        ctx.drawText(textRenderer,UiDraw.ellipsis(textRenderer,folder?tr("输入材质包所在文件夹的绝对路径"):tr("输入 ZIP 文件绝对路径；压缩包只读"),window.width()-24),x+12,y+29,UiDraw.SECONDARY,false);
        super.render(ctx,mx,my,d);
        if(!error.isEmpty())ctx.drawText(textRenderer,UiDraw.ellipsis(textRenderer,error,window.width()-24),x+12,y+71,0xffffff8a,false);
    }
    public boolean shouldPause(){return false;}public void close(){client.setScreen(parent);}
}
