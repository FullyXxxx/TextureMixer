package com.fullyx.texturemixer.ui;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.client.Catalog;
import com.fullyx.texturemixer.client.PreviewCache;
import com.fullyx.texturemixer.core.MixerConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.List;

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
        super(Text.literal(title));
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
        int lines=Math.min(textRenderer.wrapLines(Text.literal(detail),panelWidth-24).size(),target==null?5:2);
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
            Text label=Text.literal(tr("字体测试文本"));
            sampleInput=addDrawableChild(new MixerField(textRenderer,x+12,previewY+previewSize+16,panelWidth-24,UiDraw.CONTROL,label,true));
            sampleInput.setPlaceholder(label);
            sampleInput.setMaxLength(128);
            sampleInput.setText(sample);
            sampleInput.setChangedListener(value->sample=value);
        }
        int chooseWidth=Math.max(55,textRenderer.getWidth(tr("选择"))+14);
        int actionY=y+panelHeight-UiDraw.CONTROL-10;
        int chooseX=x+panelWidth-12-chooseWidth;
        choose=addDrawableChild(new FlatButton(chooseX,actionY,chooseWidth,UiDraw.CONTROL,Text.literal(tr("选择")),button->{
            if(parent.service.busy)return;
            parent.resumeFromDialog();
            client.setScreen(parent);
            selection.run();
        }).primary());
        if(target!=null&&target.kind()==MixerConfig.Kind.FONT)choose.active=false;
        choose.setTooltip(Tooltip.of(Text.literal(detail)));
        int cancelWidth=Math.max(50,textRenderer.getWidth(tr("取消"))+14);
        var cancel=addDrawableChild(new FlatButton(chooseX-cancelWidth-5,actionY,cancelWidth,UiDraw.CONTROL,Text.literal(tr("取消")),button->close()));
        setInitialFocus(fontPreview?sampleInput:cancel);
        if(target!=null)parent.focusSelectionPreviews(target,source,itemPreviews?itemModes:List.of(mode),state);
    }
    private void addPreviewControls(int controlsY){
        if(itemStateControls){
            itemStateButton=addDrawableChild(new FlatButton(x+12,controlsY,panelWidth-24,UiDraw.CONTROL,Text.literal(tr("State · ")+parent.itemStateName()),button->{
                parent.cycleItemState(target,source);button.setMessage(Text.literal(tr("State · ")+parent.itemStateName()));
            }));
            itemStateButton.active=client.world!=null;
            itemStateButton.setTooltip(Tooltip.of(Text.literal(client.world==null?tr("进入世界后可预览附魔条件外观"):tr("切换此资源包声明的附魔条件外观"))));
        }else if(target.kind()==MixerConfig.Kind.BLOCK){
            addDrawableChild(new FlatButton(x+12,controlsY,panelWidth-24,UiDraw.CONTROL,Text.literal(tr("State ")+(state+1)),button->{
                state=(state+1)%target.states().size();
                button.setMessage(Text.literal(tr("State ")+(state+1)));
                parent.focusSelectionPreview(target,source,mode,state);
            }));
        }else if(target.kind()==MixerConfig.Kind.SKY){
            int width=(panelWidth-28)/2;
            addDrawableChild(new FlatButton(x+12,controlsY,width,UiDraw.CONTROL,Text.literal(parent.timeName()),button->{parent.cycleSkyTime();button.setMessage(Text.literal(parent.timeName()));}));
            addDrawableChild(new FlatButton(x+16+width,controlsY,width,UiDraw.CONTROL,Text.literal(parent.weatherName()),button->{parent.cycleSkyWeather();button.setMessage(Text.literal(parent.weatherName()));}));
        }else if(target.kind()==MixerConfig.Kind.SOUND){
            int width=(panelWidth-28)/2;
            addDrawableChild(new FlatButton(x+12,controlsY,width,UiDraw.CONTROL,Text.literal(tr("Play")),button->parent.playSelectionAudio(target,source)));
            addDrawableChild(new FlatButton(x+16+width,controlsY,width,UiDraw.CONTROL,Text.literal(tr("Stop")),button->parent.stopSelectionAudio()));
            addDrawableChild(parent.auditionVolume(x+12,controlsY+22,panelWidth-24));
        }
    }
    @Override public void tick(){
        if(itemPreviews&&!itemStateControls&&parent.itemStates(target,source).size()>1)clearAndInit();
    }
    @Override public void render(DrawContext context,int mx,int my,float delta){
        parent.render(context,-1,-1,delta);
        context.fill(0,0,width,height,UiDraw.DIALOG_SHADE);
        com.fullyx.texturemixer.client.NativeCompat.newGuiLayer(context);
        UiDraw.window(context,x,y,panelWidth,panelHeight);
        context.drawText(textRenderer,UiDraw.ellipsis(textRenderer,title.getString(),panelWidth-24),x+12,y+10,UiDraw.TEXT,false);
        var lines=textRenderer.wrapLines(Text.literal(detail),panelWidth-24);
        for(int index=0;index<Math.min(lines.size(),target==null?5:2);index++)
            context.drawText(textRenderer,lines.get(index),x+12,y+24+index*10,UiDraw.SECONDARY,false);
        choose.active=!parent.service.busy;
        if(itemPreviews){
            parent.focusSelectionPreviews(target,source,itemModes,state);
            int cellWidth=(panelWidth-24-(itemModes.size()-1)*6)/itemModes.size();
            for(int index=0;index<itemModes.size();index++){
                var candidate=itemModes.get(index);
                int cellX=x+12+index*(cellWidth+6);
                var feedback=parent.drawSelectionPreview(context,target,source,candidate,state,cellX+(cellWidth-previewSize)/2,previewY,previewSize);
                String label=switch(candidate){case ICON->tr("Icon");case HAND->tr("Hand");case WORN->tr("Worn");};
                context.drawText(textRenderer,label,cellX+(cellWidth-textRenderer.getWidth(label))/2,previewY+previewSize+4,UiDraw.TEXT,false);
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
            context.drawText(textRenderer,tr("字体测试文本"),x+12,previewY+previewSize+4,UiDraw.SECONDARY,false);
            drawNotes(context,feedback,x+12,panelWidth-24,2);
        }else if(target!=null){
            var feedback=parent.drawSelectionPreview(context,target,source,mode,state,x+(panelWidth-previewSize)/2,previewY,previewSize);
            choose.active&=feedback.selectable();
            drawNotes(context,feedback,x+12,panelWidth-24,2);
        }
        renderWidgets(context, mx, my, delta, false);
    }
    private void drawEnchantCell(DrawContext context,int x,int cellWidth,PreviewCache.Mode selectedMode,String label) {
        var feedback=parent.drawSelectionPreview(context,target,source,selectedMode,state,x+(cellWidth-previewSize)/2,previewY,previewSize);
        context.drawText(textRenderer,label,x+(cellWidth-textRenderer.getWidth(label))/2,previewY+previewSize+4,UiDraw.TEXT,false);
        choose.active&=feedback.selectable();
        drawNotes(context,feedback,x,cellWidth,2);
    }
    private void drawNotes(DrawContext context,MixerScreen.PreviewFeedback feedback,int left,int width,int maxLines){
        var notes=textRenderer.wrapLines(Text.literal(feedback.text()),width);
        for(int index=0;index<Math.min(notes.size(),maxLines);index++)
            context.drawText(textRenderer,notes.get(index),left,statusY+index*10,feedback.selectable()?UiDraw.SECONDARY:0xffffff8a,false);
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void removed(){parent.releaseDialogPreview();}
    @Override public void close(){parent.resumeFromDialog();client.setScreen(parent);}
}
