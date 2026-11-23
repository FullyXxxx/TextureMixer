package com.fullyx.texturemixer.ui;

import com.fullyx.texturemixer.client.NativeCompat;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class PathScreen extends MixerBaseScreen {
    private final Screen parent;private final Consumer<Path> action;private EditBox path;private String error="";private UiDraw.Window window;private final boolean folder;
    public PathScreen(Screen parent,Consumer<Path> action){this(parent,false,action);}
    public PathScreen(Screen parent,boolean folder,Consumer<Path> action){super(Component.literal(folder?tr("添加文件夹"):tr("添加 ZIP 材质包")));this.parent=parent;this.folder=folder;this.action=action;}
    protected void init(){
        window=UiDraw.Window.centered(width,height,340,116);
        String previous=path==null?"":path.getValue();
        path=addRenderableWidget(new MixerField(font,window.x()+12,window.y()+47,window.width()-24,UiDraw.CONTROL,Component.literal(folder?tr("文件夹绝对路径"):tr("ZIP 文件绝对路径"))));
        path.setMaxLength(2048);path.setValue(previous);setInitialFocus(path);
        addRenderableWidget(new FlatButton(window.right()-121,window.bottom()-28,52,UiDraw.CONTROL,Component.literal(tr("取消")),b->onClose()));
        addRenderableWidget(new FlatButton(window.right()-64,window.bottom()-28,52,UiDraw.CONTROL,Component.literal(tr("添加")),b->{
            try{Path p=Path.of(path.getValue().trim());if(!p.isAbsolute())throw new IllegalArgumentException(tr("请输入绝对路径"));action.accept(p);}
            catch(Exception e){error=com.fullyx.texturemixer.client.MixerService.message(e);}
        }).primary());
    }
    public void extractRenderState(GuiGraphicsExtractor ctx,int mx,int my,float d){
        window.draw(ctx);
        int x=window.x(),y=window.y();
        ctx.text(font,title,x+12,y+12,UiDraw.TEXT,false);
        ctx.text(font,UiDraw.ellipsis(font,folder?tr("输入材质包所在文件夹的绝对路径"):tr("输入 ZIP 文件绝对路径；压缩包只读"),window.width()-24),x+12,y+29,UiDraw.SECONDARY,false);
        super.extractRenderState(ctx,mx,my,d);
        if(!error.isEmpty())ctx.text(font,UiDraw.ellipsis(font,error,window.width()-24),x+12,y+71,0xffffff8a,false);
    }
    public boolean isPauseScreen(){return false;}public void onClose(){NativeCompat.setScreen(minecraft, parent);}
}
