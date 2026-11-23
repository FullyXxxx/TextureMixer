package com.fullyx.texturemixer.ui;

import com.mojang.blaze3d.platform.InputConstants;

import com.fullyx.texturemixer.client.NativeCompat;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.client.MixerService;
import com.fullyx.texturemixer.core.TemplateStore;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class TemplatesScreen extends MixerBaseScreen {
    private final MixerScreen parent;
    private EditBox name;
    private String enteredName="",notice="";
    private UiDraw.Window window;
    private int offset,pageSize,listY,listHeight;
    private boolean loading;
    private List<TemplateStore.Summary> templates=List.of();

    public TemplatesScreen(MixerScreen parent){super(Component.literal(tr("自定义套装")));this.parent=parent;}
    @Override protected void init(){
        window=UiDraw.Window.centered(width,height,370,218);
        int x=window.x(),y=window.y(),w=window.width();
        int labelWidth=font.width(tr("套装名称"))+7;
        int saveWidth=font.width(tr("保存当前搭配"))+14;
        name=addRenderableWidget(new MixerField(font,x+12+labelWidth,y+29,w-30-labelWidth-saveWidth,UiDraw.CONTROL,Component.literal(tr("套装名称"))));
        name.setMaxLength(80);name.setValue(enteredName);name.setResponder(value->enteredName=value);
        var save=addRenderableWidget(new FlatButton(window.right()-12-saveWidth,y+29,saveWidth,UiDraw.CONTROL,Component.literal(tr("保存当前搭配")),button->save()).primary());
        save.active=!loading&&!parent.libraryImporting()&&!parent.service.busy;
        templates=parent.service.library.listTemplates();
        listY=y+55;
        listHeight=window.bottom()-listY-34;
        pageSize=Math.max(1,listHeight/22);
        offset=Math.clamp(offset,0,Math.max(0,templates.size()-pageSize));
        for(int i=offset;i<Math.min(templates.size(),offset+pageSize);i++){
            var template=templates.get(i);int rowY=listY+(i-offset)*22;
            var load=addRenderableWidget(new FlatButton(x+12,rowY,w-76,UiDraw.CONTROL,Component.literal(template.name()),button->confirmLoad(template)));
            load.setTooltip(Tooltip.create(Component.literal(template.name()+"\n"+tr("载入草稿，点击应用后生效"))));
            load.active=save.active;
            var delete=addRenderableWidget(new FlatButton(window.right()-59,rowY,47,UiDraw.CONTROL,Component.literal(tr("删除")),button->NativeCompat.setScreen(minecraft, new ChoiceScreen(this,
                    tr("删除自定义套装 ") + template.name() + "？",
                    new ChoiceScreen.Choice(tr("删除自定义套装"),()->{
                        try{parent.service.library.deleteTemplate(template.id());notice=tr("已删除自定义套装");}
                        catch(Exception error){notice=MixerService.message(error);}
                        NativeCompat.setScreen(minecraft, this);
                    }),new ChoiceScreen.Choice(tr("取消"),()->NativeCompat.setScreen(minecraft, this))))));
            delete.active=!loading;
        }
        var back=addRenderableWidget(new FlatButton(window.right()-68,window.bottom()-28,56,UiDraw.CONTROL,Component.literal(tr("返回")),button->onClose()));
        back.active=!loading;
        setInitialFocus(name);
    }
    private void save(){
        if(loading||parent.service.busy||parent.libraryImporting())return;
        try{
            var duplicate=parent.service.library.templateByName(enteredName);
            if(duplicate==null){
                parent.service.library.saveTemplate(enteredName,parent.draft);
                notice=tr("已保存当前搭配");rebuildWidgets();
            }else NativeCompat.setScreen(minecraft, new ChoiceScreen(this,tr("替换自定义套装 ") + duplicate.name() + "？",
                    new ChoiceScreen.Choice(tr("替换自定义套装"),()->{
                        try{parent.service.library.overwriteTemplate(duplicate.id(),parent.draft);notice=tr("已替换自定义套装");}
                        catch(Exception error){notice=MixerService.message(error);}
                        NativeCompat.setScreen(minecraft, this);
                    }),new ChoiceScreen.Choice(tr("取消"),()->NativeCompat.setScreen(minecraft, this))));
        }catch(Exception error){notice=MixerService.message(error);}
    }
    private void confirmLoad(TemplateStore.Summary template){
        String message=tr("载入自定义套装 ")+template.name()+"？";
        if(parent.draft.changesFrom(parent.service.committed)>0)message+="\n"+tr("载入自定义套装会替换当前草稿");
        NativeCompat.setScreen(minecraft, new ChoiceScreen(this,message,
                new ChoiceScreen.Choice(tr("确定"),()->{
                    if(parent.service.busy||parent.libraryImporting()){NativeCompat.setScreen(minecraft, this);return;}
                    NativeCompat.setScreen(minecraft, this);load(template);
                }),
                new ChoiceScreen.Choice(tr("取消"),()->NativeCompat.setScreen(minecraft, this))));
    }
    private void load(TemplateStore.Summary template){
        loading=true;notice=tr("正在载入自定义套装…");rebuildWidgets();
        parent.service.library.loadTemplate(template.id(),parent.draft).whenCompleteAsync((restored,error)->{
            loading=false;
            if(error==null)parent.acceptTemplate(restored,template.name());
            else{notice=MixerService.message(error);NativeCompat.setScreen(minecraft, this);}
        },minecraft);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor context,int mx,int my,float delta){
        window.draw(context);
        int x=window.x(),y=window.y(),w=window.width();
        context.text(font,title,x+12,y+11,UiDraw.TEXT,false);
        context.text(font,tr("套装名称"),x+12,y+34,UiDraw.SECONDARY,false);
        UiDraw.rounded(context,x+10,listY-3,w-20,listHeight+3,4,UiDraw.DETAIL);
        super.extractRenderState(context,mx,my,delta);
        if(templates.isEmpty())context.text(font,tr("尚未保存自定义套装"),x+17,listY+6,UiDraw.SECONDARY,false);
        if(!notice.isBlank())context.text(font,UiDraw.ellipsis(font,notice,w-86),x+12,window.bottom()-23,UiDraw.SECONDARY,false);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(!loading){offset-=(int)Math.signum(vertical);rebuildWidgets();}return true;
    }
    @Override protected boolean mixerKey(int key,int modifiers,java.util.function.BooleanSupplier vanilla){
        if(key==InputConstants.KEY_RETURN&&getFocused()==name){save();return true;}
        if(key==InputConstants.KEY_PAGEDOWN){offset+=pageSize;rebuildWidgets();return true;}
        if(key==InputConstants.KEY_PAGEUP){offset-=pageSize;rebuildWidgets();return true;}
        return vanilla.getAsBoolean();
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){if(!loading)NativeCompat.setScreen(minecraft, parent);}
}
