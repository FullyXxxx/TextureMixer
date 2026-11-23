package com.fullyx.texturemixer.ui;

import com.fullyx.texturemixer.client.NativeCompat;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.client.Catalog;
import com.fullyx.texturemixer.client.PreviewCache;
import com.fullyx.texturemixer.core.MixerConfig;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** The selected source has its own focused preview inside the confirmation dialog. */
public final class SelectionDialog extends MixerBaseScreen {
    private final MixerScreen parent;
    private final String detail;
    private final Runnable selection;
    private final Catalog.Target target;
    private final String source;
    private PreviewCache.Mode mode;
    private int state;
    private int x, y, panelWidth, panelHeight, previewY, previewSize, statusY;
    private FlatButton choose;
    private List<PreviewCache.Mode> itemModes=List.of();
    private boolean itemPreviews;
    private boolean enchantPreviews, fontPreview, soundPreview;
    private MixerField sampleInput;
    private String sample="Aa Bb 0123";
    private boolean itemStateControls;
    private FlatButton itemStateButton;

    public SelectionDialog(MixerScreen parent,String title,String detail,Runnable selection){
        this(parent,title,detail,selection,null,null);
    }
    public SelectionDialog(MixerScreen parent,String title,String detail,Runnable selection,Catalog.Target target,String source){
        super(Component.literal(title));
        this.parent=parent;
        this.detail=detail;
        this.selection=selection;
        this.target=target;
        this.source=source;
        mode=target!=null&&target.kind()==MixerConfig.Kind.ITEM?parent.previewMode():PreviewCache.Mode.ICON;
        state=target!=null&&target.kind()==MixerConfig.Kind.BLOCK?parent.previewState():0;
    }
    @Override protected void init(){
        itemPreviews=target!=null&&target.kind()==MixerConfig.Kind.ITEM;
        enchantPreviews=target!=null&&target.kind()==MixerConfig.Kind.ENCHANT;
        fontPreview=target!=null&&target.kind()==MixerConfig.Kind.FONT;
        soundPreview=target!=null&&target.kind()==MixerConfig.Kind.SOUND;
        itemModes=itemPreviews?(MixerScreen.supportsWorn(target)?List.of(PreviewCache.Mode.ICON,PreviewCache.Mode.HAND,PreviewCache.Mode.WORN):List.of(PreviewCache.Mode.ICON,PreviewCache.Mode.HAND)):List.of();
        itemStateControls=itemPreviews&&parent.itemStates(target,source).size()>1;
        panelWidth=Math.min(itemModes.size()==3?340:288,width-24);
        int lines=Math.min(font.split(Component.literal(detail),panelWidth-24).size(),target==null?5:2);
        previewSize=height<280?50:enchantPreviews?76:62;
        if(itemPreviews)previewSize=Math.min(previewSize,(panelWidth-24-(itemModes.size()-1)*6)/itemModes.size());
        int previewOffset=29+lines*10;
        boolean controls=itemStateControls||target!=null&&(target.kind()==MixerConfig.Kind.SOUND
                ||target.kind()==MixerConfig.Kind.BLOCK&&target.states().size()>1
                ||target.kind()==MixerConfig.Kind.SKY&&!target.id().equals("weather"));
        boolean splitPreviews=itemPreviews||enchantPreviews;
        int statusOffset=previewOffset+previewSize+(splitPreviews?18:5)+(controls?22:0)+(soundPreview?22:0)+(fontPreview?30:0);
        panelHeight=target==null?62+lines*10:statusOffset+(splitPreviews?62:52);
        x=(width-panelWidth)/2;
        y=(height-panelHeight)/2;
        previewY=y+previewOffset;
        statusY=y+statusOffset;
        if(controls)addPreviewControls(previewY+previewSize+(itemPreviews?18:5));
        if(fontPreview){
            Component label=Component.literal(tr("字体测试文本"));
            sampleInput=addRenderableWidget(new MixerField(font,x+12,previewY+previewSize+16,panelWidth-24,UiDraw.CONTROL,label,true));
            sampleInput.setHint(label);
            sampleInput.setMaxLength(128);
            sampleInput.setValue(sample);
            sampleInput.setResponder(value->sample=value);
        }
        int chooseWidth=Math.max(55,font.width(tr("选择"))+14);
        int actionY=y+panelHeight-UiDraw.CONTROL-10;
        int chooseX=x+panelWidth-12-chooseWidth;
        choose=addRenderableWidget(new FlatButton(chooseX,actionY,chooseWidth,UiDraw.CONTROL,Component.literal(tr("选择")),button->{
            if(parent.service.busy)return;
            parent.resumeFromDialog();
            NativeCompat.setScreen(minecraft, parent);
            selection.run();
        }).primary());
        if(target!=null&&target.kind()==MixerConfig.Kind.FONT)choose.active=false;
        choose.setTooltip(Tooltip.create(Component.literal(detail)));
        int cancelWidth=Math.max(50,font.width(tr("取消"))+14);
        var cancel=addRenderableWidget(new FlatButton(chooseX-cancelWidth-5,actionY,cancelWidth,UiDraw.CONTROL,Component.literal(tr("取消")),button->onClose()));
        setInitialFocus(fontPreview?sampleInput:cancel);
        if(target!=null)parent.focusSelectionPreviews(target,source,itemPreviews?itemModes:List.of(mode),state);
    }
    private void addPreviewControls(int controlsY){
        if(itemStateControls){
            itemStateButton=addRenderableWidget(new FlatButton(x+12,controlsY,panelWidth-24,UiDraw.CONTROL,Component.literal(tr("State · ")+parent.itemStateName()),button->{
                parent.cycleItemState(target,source);button.setMessage(Component.literal(tr("State · ")+parent.itemStateName()));
            }));
            itemStateButton.active=minecraft.level!=null;
            itemStateButton.setTooltip(Tooltip.create(Component.literal(minecraft.level==null?tr("进入世界后可预览附魔条件外观"):tr("切换此资源包声明的附魔条件外观"))));
        }else if(target.kind()==MixerConfig.Kind.BLOCK){
            addRenderableWidget(new FlatButton(x+12,controlsY,panelWidth-24,UiDraw.CONTROL,Component.literal(tr("State ")+(state+1)),button->{
                state=(state+1)%target.states().size();
                button.setMessage(Component.literal(tr("State ")+(state+1)));
                parent.focusSelectionPreview(target,source,mode,state);
            }));
        }else if(target.kind()==MixerConfig.Kind.SKY){
            int width=(panelWidth-28)/2;
            addRenderableWidget(new FlatButton(x+12,controlsY,width,UiDraw.CONTROL,Component.literal(parent.timeName()),button->{parent.cycleSkyTime();button.setMessage(Component.literal(parent.timeName()));}));
            addRenderableWidget(new FlatButton(x+16+width,controlsY,width,UiDraw.CONTROL,Component.literal(parent.weatherName()),button->{parent.cycleSkyWeather();button.setMessage(Component.literal(parent.weatherName()));}));
        }else if(target.kind()==MixerConfig.Kind.SOUND){
            int width=(panelWidth-28)/2;
            addRenderableWidget(new FlatButton(x+12,controlsY,width,UiDraw.CONTROL,Component.literal(tr("Play")),button->parent.playSelectionAudio(target,source)));
            addRenderableWidget(new FlatButton(x+16+width,controlsY,width,UiDraw.CONTROL,Component.literal(tr("Stop")),button->parent.stopSelectionAudio()));
            addRenderableWidget(parent.auditionVolume(x+12,controlsY+22,panelWidth-24));
        }
    }
    @Override public void tick(){
        if(itemPreviews&&!itemStateControls&&parent.itemStates(target,source).size()>1)rebuildWidgets();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor context,int mx,int my,float delta){
        parent.extractRenderState(context,-1,-1,delta);
        context.fill(0,0,width,height,UiDraw.DIALOG_SHADE);
        com.fullyx.texturemixer.client.NativeCompat.newGuiLayer(context);
        UiDraw.window(context,x,y,panelWidth,panelHeight);
        context.text(font,UiDraw.ellipsis(font,title.getString(),panelWidth-24),x+12,y+10,UiDraw.TEXT,false);
        var lines=font.split(Component.literal(detail),panelWidth-24);
        for(int index=0;index<Math.min(lines.size(),target==null?5:2);index++)
            context.text(font,lines.get(index),x+12,y+24+index*10,UiDraw.SECONDARY,false);
        choose.active=!parent.service.busy;
        if(itemPreviews){
            parent.focusSelectionPreviews(target,source,itemModes,state);
            int cellWidth=(panelWidth-24-(itemModes.size()-1)*6)/itemModes.size();
            for(int index=0;index<itemModes.size();index++){
                var candidate=itemModes.get(index);
                int cellX=x+12+index*(cellWidth+6);
                var feedback=parent.drawSelectionPreview(context,target,source,candidate,state,cellX+(cellWidth-previewSize)/2,previewY,previewSize);
                String label=switch(candidate){case ICON->tr("Icon");case HAND->tr("Hand");case WORN->tr("Worn");};
                context.text(font,label,cellX+(cellWidth-font.width(label))/2,previewY+previewSize+4,UiDraw.TEXT,false);
                choose.active&=feedback.selectable();
                drawNotes(context,feedback,cellX,cellWidth,3);
            }
        }else if(enchantPreviews){
            int cellWidth=(panelWidth-30)/2;
            boolean armor=target.id().endsWith("_armor.png");
            drawEnchantCell(context,x+12,cellWidth,armor?PreviewCache.Mode.WORN:PreviewCache.Mode.ICON,tr(armor?"盔甲架":"物品栏附魔"));
            drawEnchantCell(context,x+18+cellWidth,cellWidth,armor?PreviewCache.Mode.ICON:PreviewCache.Mode.HAND,tr(armor?"材质贴图":"手持附魔"));
        }else if(fontPreview){
            var feedback=parent.drawFontSample(context,target,source,sample,x+12,previewY,panelWidth-24,previewSize);
            choose.active&=feedback.selectable();
            context.text(font,tr("字体测试文本"),x+12,previewY+previewSize+4,UiDraw.SECONDARY,false);
            drawNotes(context,feedback,x+12,panelWidth-24,2);
        }else if(target!=null){
            var feedback=parent.drawSelectionPreview(context,target,source,mode,state,x+(panelWidth-previewSize)/2,previewY,previewSize);
            choose.active&=feedback.selectable();
            drawNotes(context,feedback,x+12,panelWidth-24,2);
        }
        super.extractRenderState(context,mx,my,delta);
    }
    private void drawEnchantCell(GuiGraphicsExtractor context,int x,int cellWidth,PreviewCache.Mode selectedMode,String label) {
        var feedback=parent.drawSelectionPreview(context,target,source,selectedMode,state,x+(cellWidth-previewSize)/2,previewY,previewSize);
        context.text(font,label,x+(cellWidth-font.width(label))/2,previewY+previewSize+4,UiDraw.TEXT,false);
        choose.active&=feedback.selectable();
        drawNotes(context,feedback,x,cellWidth,2);
    }
    private void drawNotes(GuiGraphicsExtractor context,MixerScreen.PreviewFeedback feedback,int left,int width,int maxLines){
        var notes=font.split(Component.literal(feedback.text()),width);
        for(int index=0;index<Math.min(notes.size(),maxLines);index++)
            context.text(font,notes.get(index),left,statusY+index*10,feedback.selectable()?UiDraw.SECONDARY:0xffffff8a,false);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){parent.releaseDialogPreview();}
    @Override public void onClose(){parent.resumeFromDialog();NativeCompat.setScreen(minecraft, parent);}
}
