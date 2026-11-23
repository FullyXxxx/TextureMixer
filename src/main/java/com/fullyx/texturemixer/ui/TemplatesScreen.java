package com.fullyx.texturemixer.ui;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.client.MixerService;
import com.fullyx.texturemixer.core.TemplateStore;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.List;

public final class TemplatesScreen extends MixerBaseScreen {
    private final MixerScreen parent;
    private TextFieldWidget name;
    private String enteredName="",notice="";
    private UiDraw.Window window;
    private int offset,pageSize,listY,listHeight;
    private boolean loading;
    private List<TemplateStore.Summary> templates=List.of();

    public TemplatesScreen(MixerScreen parent){super(Text.literal(tr("自定义套装")));this.parent=parent;}
    @Override protected void init(){
        window=UiDraw.Window.centered(width,height,370,218);
        int x=window.x(),y=window.y(),w=window.width();
        int labelWidth=textRenderer.getWidth(tr("套装名称"))+7;
        int saveWidth=textRenderer.getWidth(tr("保存当前搭配"))+14;
        name=addDrawableChild(new MixerField(textRenderer,x+12+labelWidth,y+29,w-30-labelWidth-saveWidth,UiDraw.CONTROL,Text.literal(tr("套装名称"))));
        name.setMaxLength(80);name.setText(enteredName);name.setChangedListener(value->enteredName=value);
        var save=addDrawableChild(new FlatButton(window.right()-12-saveWidth,y+29,saveWidth,UiDraw.CONTROL,Text.literal(tr("保存当前搭配")),button->save()).primary());
        save.active=!loading&&!parent.libraryImporting()&&!parent.service.busy;
        templates=parent.service.library.listTemplates();
        listY=y+55;
        listHeight=window.bottom()-listY-34;
        pageSize=Math.max(1,listHeight/22);
        offset=Math.clamp(offset,0,Math.max(0,templates.size()-pageSize));
        for(int i=offset;i<Math.min(templates.size(),offset+pageSize);i++){
            var template=templates.get(i);int rowY=listY+(i-offset)*22;
            var load=addDrawableChild(new FlatButton(x+12,rowY,w-76,UiDraw.CONTROL,Text.literal(template.name()),button->confirmLoad(template)));
            load.setTooltip(Tooltip.of(Text.literal(template.name()+"\n"+tr("载入草稿，点击应用后生效"))));
            load.active=save.active;
            var delete=addDrawableChild(new FlatButton(window.right()-59,rowY,47,UiDraw.CONTROL,Text.literal(tr("删除")),button->client.setScreen(new ChoiceScreen(this,
                    tr("删除自定义套装 ") + template.name() + "？",
                    new ChoiceScreen.Choice(tr("删除自定义套装"),()->{
                        try{parent.service.library.deleteTemplate(template.id());notice=tr("已删除自定义套装");}
                        catch(Exception error){notice=MixerService.message(error);}
                        client.setScreen(this);
                    }),new ChoiceScreen.Choice(tr("取消"),()->client.setScreen(this))))));
            delete.active=!loading;
        }
        var back=addDrawableChild(new FlatButton(window.right()-68,window.bottom()-28,56,UiDraw.CONTROL,Text.literal(tr("返回")),button->close()));
        back.active=!loading;
        setInitialFocus(name);
    }
    private void save(){
        if(loading||parent.service.busy||parent.libraryImporting())return;
        try{
            var duplicate=parent.service.library.templateByName(enteredName);
            if(duplicate==null){
                parent.service.library.saveTemplate(enteredName,parent.draft);
                notice=tr("已保存当前搭配");clearAndInit();
            }else client.setScreen(new ChoiceScreen(this,tr("替换自定义套装 ") + duplicate.name() + "？",
                    new ChoiceScreen.Choice(tr("替换自定义套装"),()->{
                        try{parent.service.library.overwriteTemplate(duplicate.id(),parent.draft);notice=tr("已替换自定义套装");}
                        catch(Exception error){notice=MixerService.message(error);}
                        client.setScreen(this);
                    }),new ChoiceScreen.Choice(tr("取消"),()->client.setScreen(this))));
        }catch(Exception error){notice=MixerService.message(error);}
    }
    private void confirmLoad(TemplateStore.Summary template){
        String message=tr("载入自定义套装 ")+template.name()+"？";
        if(parent.draft.changesFrom(parent.service.committed)>0)message+="\n"+tr("载入自定义套装会替换当前草稿");
        client.setScreen(new ChoiceScreen(this,message,
                new ChoiceScreen.Choice(tr("确定"),()->{
                    if(parent.service.busy||parent.libraryImporting()){client.setScreen(this);return;}
                    client.setScreen(this);load(template);
                }),
                new ChoiceScreen.Choice(tr("取消"),()->client.setScreen(this))));
    }
    private void load(TemplateStore.Summary template){
        loading=true;notice=tr("正在载入自定义套装…");clearAndInit();
        parent.service.library.loadTemplate(template.id(),parent.draft).whenCompleteAsync((restored,error)->{
            loading=false;
            if(error==null)parent.acceptTemplate(restored,template.name());
            else{notice=MixerService.message(error);client.setScreen(this);}
        },client);
    }
    @Override public void render(DrawContext context,int mx,int my,float delta){
        window.draw(context);
        int x=window.x(),y=window.y(),w=window.width();
        context.drawText(textRenderer,title,x+12,y+11,UiDraw.TEXT,false);
        context.drawText(textRenderer,tr("套装名称"),x+12,y+34,UiDraw.SECONDARY,false);
        UiDraw.rounded(context,x+10,listY-3,w-20,listHeight+3,4,UiDraw.DETAIL);
        super.render(context,mx,my,delta);
        if(templates.isEmpty())context.drawText(textRenderer,tr("尚未保存自定义套装"),x+17,listY+6,UiDraw.SECONDARY,false);
        if(!notice.isBlank())context.drawText(textRenderer,UiDraw.ellipsis(textRenderer,notice,w-86),x+12,window.bottom()-23,UiDraw.SECONDARY,false);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(!loading){offset-=(int)Math.signum(vertical);clearAndInit();}return true;
    }
    @Override protected boolean mixerKey(int key,int modifiers,java.util.function.BooleanSupplier vanilla){
        if(key==GLFW.GLFW_KEY_ENTER&&getFocused()==name){save();return true;}
        if(key==GLFW.GLFW_KEY_PAGE_DOWN){offset+=pageSize;clearAndInit();return true;}
        if(key==GLFW.GLFW_KEY_PAGE_UP){offset-=pageSize;clearAndInit();return true;}
        return vanilla.getAsBoolean();
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){if(!loading)client.setScreen(parent);}
}
