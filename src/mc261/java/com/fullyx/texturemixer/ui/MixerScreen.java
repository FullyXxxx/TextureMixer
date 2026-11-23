package com.fullyx.texturemixer.ui;

import com.mojang.blaze3d.platform.InputConstants;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.client.*;
import com.fullyx.texturemixer.core.*;
import com.fullyx.texturemixer.sky.SkyPreviewCache;
import com.fullyx.texturemixer.sky.SkyScene;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MixerScreen extends MixerBaseScreen {
    public final MixerService service;
    public MixerConfig draft;
    private MixerConfig.Kind tab=MixerConfig.Kind.ITEM;
    private Catalog.Target target;
    private PreviewCache.Mode mode=PreviewCache.Mode.ICON;
    private ItemPreviewState itemVariant=ItemPreviewState.NORMAL;
    private FlatButton itemStateButton;
    private final Map<String,String> queries=new HashMap<>();
    private final Map<String,Double> scrolls=new HashMap<>();
    private String query="",notice="",focusNote="";
    private EditBox search;
    private FlatButton apply,undo,reset,statusView,clearSearch;
    private MixerScreen editor;
    private int left,top,panelWidth,panelHeight,gridTop,gridBottom,columns,tile,rowHeight,stateIndex,contentLeft,contentWidth;
    private double scrollTarget;
    private long lastFrame;
    private int previewTime=18000,weather;
    private long previewDay;
    private String previewBiome="minecraft:plains";
    private double previewHeight=80;
    private boolean sourcesChanged;
    private boolean operationFailed;
    private boolean picking;
    private boolean importingFolder;
    private boolean discovering, discovered;
    private long nextDiscovery;
    private Screen previewReturn;
    private String previewSource;
    private boolean packPage=true, suspended, waitingForCatalog, navScrolled;
    private final Runnable returnToParent;
    private final PackPane packPane;
    private final Deque<Edit> history=new ArrayDeque<>();
    private record Edit(String description,MixerConfig before) {}
    private String undoToast="";
    private long undoToastStarted;
    private FlatButton stepUndo,done;
    private double scroll;
    private List<Card> cards=List.of();
    private List<Object> visible=List.of();
    private int lastRows=-1,dirtyCount;
    private final SkyPreviewCache skyPreviews;
    private final ResourcePreviewCache resourcePreviews;
    private final FontPreview fontPreview;
    private final EnchantPreview enchantPreview=new EnchantPreview();
    private final AudioPreview audioPreview;
    private final List<FlatButton> categoryButtons=new ArrayList<>();
    private int navOffset,navCapacity,navTop,navBottom;
    private static final List<MixerConfig.Kind> TOP_CATEGORIES=List.of(MixerConfig.Kind.ITEM,MixerConfig.Kind.BLOCK,
            MixerConfig.Kind.SKY,MixerConfig.Kind.ENTITY,MixerConfig.Kind.PARTICLE,MixerConfig.Kind.HUD,
            MixerConfig.Kind.SOUND,MixerConfig.Kind.ENCHANT);
    private static final int PAGE_COUNT=TOP_CATEGORIES.size()+1;
    public MixerScreen(MixerService service){this(service,()->NativeCompat.setScreen(net.minecraft.client.Minecraft.getInstance(), null));}
    public MixerScreen(MixerService service,Runnable returnToParent){
        super(Component.literal("Resourcepack"));this.service=service;this.returnToParent=returnToParent;
        draft=service.committed.copy();skyPreviews=service.skyPreviews;resourcePreviews=service.resourcePreviews;
        fontPreview=new FontPreview(service);
        audioPreview=new AudioPreview(service);packPane=new PackPane(this);waitingForCatalog=service.catalog==null;
    }
    public void edit(String description,Runnable action){
        MixerConfig before=draft.copy();action.run();
        if(!before.renderSignature().equals(draft.renderSignature())||!before.sources.equals(draft.sources)){
            history.push(new Edit(description,before));
            if(history.size()>100)history.removeLast();
            operationFailed=false;notice=description+tr("，应用后生效");
        }
    }
    public void moveMain(String source,int direction){edit((direction<0?tr("上移主包 "):tr("下移主包 "))+sourceName(source),()->draft.moveMain(source,direction));}
    public void removeMain(String source){edit(tr("移除主包 ")+sourceName(source),()->draft.setMainStack(draft.mainStack().stream().filter(id->!id.equals(source)).toList()));rebuildWidgets();}
    private void undoStep(){
        if(service.busy||history.isEmpty())return;
        Edit operation=history.pop();MixerConfig restored=operation.before().copy();
        restored.sources.putAll(draft.sources);
        for(String source:draft.candidateSources)restored.addCandidate(source);
        restored.enabled=draft.enabled;draft=restored;operationFailed=false;
        undoToast=tr("已撤销 · ")+operation.description();undoToastStarted=System.nanoTime();notice=undoToast+tr("，应用后生效");
        rebuildWidgets();
    }
    void resumeFromDialog(){suspended=false;audioPreview.stop();refreshCards();}
    void releaseDialogPreview(){fontPreview.close();enchantPreview.clear();EnchantPreview.clearQueued();}
    void addPackWidget(net.minecraft.client.gui.components.AbstractWidget widget){addRenderableWidget(widget);}
    void removePackWidget(net.minecraft.client.gui.components.AbstractWidget widget){removeWidget(widget);}
    void rebuildPackLists(){rebuildWidgets();}
    void clearPackSearch(){search.setValue("");}
    void showPackDetails(String source){NativeCompat.setScreen(minecraft, new PackDetailsScreen(this,this,source));}
    public static MixerScreen preview(MixerScreen parent, String source, MixerConfig.Kind category, Screen returnTo) {
        MixerScreen screen = new MixerScreen(parent.service);
        screen.draft = parent.draft.copy();
        screen.previewSource = source;
        screen.previewReturn = returnTo;
        screen.tab = category;
        screen.packPage=false;
        screen.editor=parent;
        return screen;
    }
    public static MixerScreen mainPreview(MixerScreen parent,String source,Screen returnTo) {
        return preview(parent,source,MixerConfig.Kind.ITEM,returnTo);
    }
    public void previewMain(String source,Screen returnTo){
        if(service.busy||libraryImporting())return;
        String action=source.equals(MixerConfig.VANILLA)?tr("使用 Vanilla 基础资源"):tr("添加主包 ")+sourceName(source);
        edit(action,()->{
            if(source.equals(MixerConfig.VANILLA))draft.setMainStack(List.of());
            else if(!draft.mainStack().contains(source))draft.addMain(source);
        });
        if(NativeCompat.screen(minecraft)!=this)NativeCompat.setScreen(minecraft, this);else rebuildWidgets();
    }
    private void previewChoice(Catalog.Target selected,String source){previewChoice(selected,source,false);}
    private void previewChoice(Catalog.Target selected,String source,boolean followMain){
        String action=followMain?selected.name()+tr(" 跟随主包"):selected.name()+" → "+sourceName(source);
        if(selected.kind()==MixerConfig.Kind.SOUND)playAudio(selected,source);
        confirmSelection(tr("是否选择此材质？"),selected.name()+"\n"+(followMain?tr("跟随主材质包"):sourceName(source)),()->{
            MixerScreen owner=editor==null?this:editor;
            owner.edit(action,()->{
                if(followMain)owner.draft.overrides(selected.kind()).remove(selected.id());
                else owner.draft.overrides(selected.kind()).put(selected.id(),source);
            });
            if(owner!=this)draft=owner.draft.copy();
            audioPreview.stop();refreshCards();
        },selected,source);
    }
    private void confirmSelection(String heading,String detail,Runnable selection){
        confirmSelection(heading,detail,selection,null,null);
    }
    private void confirmSelection(String heading,String detail,Runnable selection,Catalog.Target previewTarget,String source){
        if(service.busy)return;
        suspended=true;NativeCompat.setScreen(minecraft, new SelectionDialog(this,heading,detail,selection,previewTarget,source));
    }
    PreviewCache.Mode previewMode(){return mode;}
    int previewState(){return stateIndex;}
    List<ItemPreviewState> itemStates(Catalog.Target selected,String source){
        Set<ItemPreviewState> states=new LinkedHashSet<>();states.add(ItemPreviewState.NORMAL);
        for(String id:source==null?sourceIds():List.of(source)){
            var entry=service.previews.getIfPresent(selected,id,0,PreviewCache.Mode.ICON);
            if(entry!=null&&entry.ready())states.addAll(entry.arena.previewStates);
        }
        return List.copyOf(states);
    }
    String itemStateName(){return itemVariant.label();}
    void cycleItemState(Catalog.Target selected,String source){
        var states=itemStates(selected,source);
        itemVariant=states.get(Math.floorMod(states.indexOf(itemVariant)+1,states.size()));
    }
    static boolean supportsWorn(Catalog.Target target){
        return target.equipment()!=null||target.stack().get(net.minecraft.core.component.DataComponents.EQUIPPABLE)!=null
                ||Set.of("minecraft:player_head","minecraft:carved_pumpkin").contains(target.id());
    }
    void focusSelectionPreview(Catalog.Target selected,String source,PreviewCache.Mode selectedMode,int state){
        focusSelectionPreviews(selected,source,List.of(selectedMode),state);
    }
    void focusSelectionPreviews(Catalog.Target selected,String source,List<PreviewCache.Mode> modes,int state){
        if(selected.kind()==MixerConfig.Kind.ITEM||selected.kind()==MixerConfig.Kind.BLOCK){
            Set<String> keys=new HashSet<>();
            for(var selectedMode:modes){
                if(selectedMode==PreviewCache.Mode.WORN&&(!supportsWorn(selected)||minecraft.level==null))continue;
                service.previews.get(selected,source,state,selectedMode);
                keys.add(PreviewCache.key(selected,source,state,selectedMode));
            }
            service.previews.retainPending(keys);
        }else service.previews.retainPending(Set.of());
        if(selected.kind()==MixerConfig.Kind.FONT)fontPreview.request(selected,source);
    }
    AuditionVolume auditionVolume(int x,int y,int width){return new AuditionVolume(x,y,width,audioPreview);}
    void stopSelectionAudio(){audioPreview.stop();}
    void playSelectionAudio(Catalog.Target target,String source){playAudio(target,source);}
    record PreviewFeedback(String text,boolean selectable) {}
    PreviewFeedback drawFontSample(GuiGraphicsExtractor context,Catalog.Target selected,String source,String sample,int x,int y,int width,int height){
        UiDraw.rounded(context,x,y,width,height,4,UiDraw.DETAIL);
        fontPreview.request(selected,source);
        fontPreview.draw(context,x,y,width,height,sample);
        String note=fontPreview.error()!=null?fontPreview.error():!fontPreview.ready()?tr("正在加载预览…")
                :!fontPreview.custom()&&!source.equals(MixerConfig.VANILLA)?tr("该来源的此项外观使用原版"):"";
        return new PreviewFeedback(note,fontPreview.ready());
    }
    PreviewFeedback drawSelectionPreview(GuiGraphicsExtractor context,Catalog.Target selected,String source,PreviewCache.Mode selectedMode,int state,int x,int y,int size){
        UiDraw.rounded(context,x,y,size,size,4,UiDraw.DETAIL);
        if(selected.kind()==MixerConfig.Kind.ITEM||selected.kind()==MixerConfig.Kind.BLOCK){
            if(selectedMode==PreviewCache.Mode.WORN&&!supportsWorn(selected))return new PreviewFeedback(tr("此物品无穿戴效果"),true);
            if(selectedMode==PreviewCache.Mode.WORN&&minecraft.level==null)return new PreviewFeedback(tr("进入世界后可查看穿戴预览"),true);
            var entry=service.previews.get(selected,source,state,selectedMode);
            if(entry.ready())entry.arena.draw(context,selected,selectedMode,x,y,size,new ScreenRectangle(x,y,size,size),itemVariant);
            String text=entry.error!=null?entry.error:!entry.ready()?tr("正在加载预览…"):entry.status==GraphCompiler.Status.VANILLA_FALLBACK&&!source.equals(MixerConfig.VANILLA)?tr("该来源的此项外观使用原版"):entry.note==null?"":entry.note;
            return new PreviewFeedback(text,entry.error==null||entry.budgetLimited);
        }
        if(selected.kind()==MixerConfig.Kind.SKY){
            var entry=skyPreviews.get(selected.id(),source,skyContext());
            skyPreviews.draw(entry,context,x,y,size);
            return new PreviewFeedback(entry.error!=null?entry.error:entry.texture==null?tr("正在加载预览…"):"",entry.error==null);
        }
        if(selected.kind()==MixerConfig.Kind.SOUND){
            for(int step=0;step<16;step++)context.fill(x+size/2-5+step,y+size/2-15+step,x+size/2-4+step,y+size/2+16-step,0xffa0a0a0);
            return new PreviewFeedback(audioPreview.status,true);
        }
        var entry=resourcePreviews.get(selected,source);
        if(selected.kind()==MixerConfig.Kind.ENCHANT){
            if(entry.error!=null)return new PreviewFeedback(entry.error,false);
            var effect=resourcePreviews.glint(entry,selected);
            if(effect==null)return new PreviewFeedback(tr("正在加载预览…"),true);
            if(!effect.armor())enchantPreview.drawSword(context,effect,selectedMode==PreviewCache.Mode.HAND,x,y,size);
            else if(selectedMode==PreviewCache.Mode.WORN){
                if(minecraft.level==null)return new PreviewFeedback(tr("进入世界后可查看穿戴预览"),true);
                enchantPreview.draw(context,effect,x,y,size);
            }else resourcePreviews.draw(entry,context,x,y,size);
            return new PreviewFeedback("",true);
        }
        resourcePreviews.draw(entry,context,x,y,size);
        return new PreviewFeedback(entry.error!=null?entry.error:entry.texture==null?tr("正在加载预览…"):"",entry.error==null);
    }
    private void confirmReset(){
        if(service.busy||libraryImporting())return;
        NativeCompat.setScreen(minecraft, new ChoiceScreen(this,tr("重置混搭？各类别单项选择将恢复为跟随主材质包。"),
                new ChoiceScreen.Choice(tr("确定"),()->{
                    edit(tr("重置混搭"),draft::clearOverrides);
                    audioPreview.stop();NativeCompat.setScreen(minecraft, this);
                }),new ChoiceScreen.Choice(tr("取消"),()->NativeCompat.setScreen(minecraft, this))));
    }
    public boolean libraryImporting() { return importingFolder || discovering && !discovered; }
    public static String plainName(String value) {
        String plain = net.minecraft.ChatFormatting.stripFormatting(value);
        return plain == null ? "" : plain;
    }
    public void acceptTemplate(MixerConfig restored, String name) {
        edit(tr("载入自定义套装 ")+name,()->draft=restored);
        sourcesChanged = true;
        operationFailed = false;
        notice = tr("已载入 ") + name + tr("，应用后生效");
        service.previews.invalidate();
        skyPreviews.invalidateCustom();
        resourcePreviews.invalidateCustom();
        NativeCompat.setScreen(minecraft, this);
    }
    public Catalog.Target target(){return target;}
    public MixerConfig.Kind tab(){return tab;}
    private String page(){return (packPage?"packs":tab.toString())+"|"+(target==null?"list":target.id());}
    private void savePage(){queries.put(page(),query);scrolls.put(page(),scroll);}
    private void loadPage(){query=queries.getOrDefault(page(),"");scroll=scrolls.getOrDefault(page(),0.0);scrollTarget=scroll;lastRows=-1;}
    @Override protected void init(){
        var window=UiDraw.Window.centered(width,height,Math.min(560,Math.max(312,width*4/5)),Math.min(330,Math.max(222,height*4/5)));
        panelWidth=window.width();panelHeight=window.height();left=window.x();top=window.y();
        contentLeft=left+10;contentWidth=panelWidth-20;
        tile=panelWidth<400||panelHeight<245?34:40;
        rowHeight=tile+24;
        columns=Math.max(1,(contentWidth-10)/(tile+9));
        apply=undo=reset=stepUndo=done=null;
        itemStateButton=null;
        navTop=top+24;navBottom=navTop+UiDraw.CONTROL;
        navCapacity=navigationCapacity();
        int selected=packPage?0:TOP_CATEGORIES.indexOf(tab.interfaceCategory()?MixerConfig.Kind.HUD:tab)+1;
        navOffset=Math.clamp(navOffset,0,PAGE_COUNT-navCapacity);
        if(!navScrolled)navOffset=Math.clamp(navOffset,Math.max(0,selected-navCapacity+1),Math.min(selected,PAGE_COUNT-navCapacity));
        categoryButtons.clear();refreshNavigation();
        int toolbarY=navBottom+5;
        if(!packPage&&tab.interfaceCategory()){
            addInterfaceCategories(toolbarY);
            toolbarY+=UiDraw.CONTROL+4;
        }
        int detailY=toolbarY+UiDraw.CONTROL+4;
        int searchLabelWidth=font.width(tr("Search"))+8;
        search=addRenderableWidget(new MixerField(font,contentLeft+searchLabelWidth,toolbarY,contentWidth-searchLabelWidth-20,UiDraw.CONTROL,Component.literal(tr("Search"))));
        search.setMaxLength(128);search.setValue(query);search.setResponder(value->{query=value;scroll=scrollTarget=0;lastRows=-1;if(packPage)packPane.refresh(query);else refreshCards();});
        clearSearch=addRenderableWidget(new FlatButton(left+panelWidth-26,toolbarY,16,UiDraw.CONTROL,Component.literal("×"),b->{search.setValue("");setFocused(search);search.setFocused(true);}).symbol(UiDraw.Symbol.CLOSE).quiet());
        clearSearch.narrationLabel=tr("Clear Search");clearSearch.setTooltip(Tooltip.create(Component.literal(tr("Clear Search"))));
        if(!packPage&&target!=null){
            tool(contentLeft,detailY,36,"返回",b->back()).quiet();
            int followWidth=buttonWidth(tr("跟随主包"),56);
            if(previewReturn==null)tool(contentLeft+contentWidth-followWidth,detailY,followWidth,"跟随主包",b->previewChoice(target,draft.mainView(),true));
            int modeX=contentLeft+41;
            if(tab==MixerConfig.Kind.ITEM)for(PreviewCache.Mode candidate:PreviewCache.Mode.values()){
                if(candidate==PreviewCache.Mode.WORN&&!supportsWorn(target))continue;
                String label=candidate.name().charAt(0)+candidate.name().substring(1).toLowerCase(Locale.ROOT);
                int modeWidth=buttonWidth(tr(label),30);
                FlatButton button=tool(modeX,detailY,modeWidth,label,b->{mode=candidate;rebuildWidgets();});
                button.selected=mode==candidate;modeX+=modeWidth+3;
            }
            if(tab==MixerConfig.Kind.ITEM){
                itemStateButton=tool(modeX,detailY,38,"State",b->cycleItemState(target,null));
                itemStateButton.visible=false;
            }
            if(tab==MixerConfig.Kind.BLOCK&&target.blockState().getBlock().getStateDefinition().getPossibleStates().size()>1)
                addRenderableWidget(new FlatButton(modeX,detailY,58,UiDraw.CONTROL,Component.literal(tr("State ")+(stateIndex+1)),b->{stateIndex=(stateIndex+1)%target.blockState().getBlock().getStateDefinition().getPossibleStates().size();rebuildWidgets();}));
            if(tab==MixerConfig.Kind.SOUND){
                tool(modeX,detailY,34,"Play",b->playAudio(target,previewReturn==null?draft.source(tab,target.id()):previewSource));
                tool(modeX+38,detailY,34,"Stop",b->audioPreview.stop());
                int sliderWidth=Math.min(140,contentLeft+contentWidth-modeX-76-buttonWidth(tr("跟随主包"),54)-6);
                if(sliderWidth>=62)addRenderableWidget(auditionVolume(modeX+76,detailY,sliderWidth));
            }
        }
        if(!packPage&&skyScenePreview()){
            int conditionX=contentLeft+(target==null?0:41);
            var conditions=addRenderableWidget(new FlatButton(conditionX,detailY,Math.min(142,contentWidth-(target==null?0:108)),UiDraw.CONTROL,Component.literal(timeName()+" / "+weatherName()+tr(" · 条件")),b->NativeCompat.setScreen(minecraft, new SkyConditionsScreen(this))));
            conditions.setTooltip(Tooltip.create(Component.literal(skySummary())));
        }
        int footer=top+panelHeight-UiDraw.CONTROL-9;
        int settingsWidth=buttonWidth(tr("设置"),44), setsWidth=buttonWidth(tr("自定义套装"),66);
        int folderWidth=packPage?buttonWidth(tr("打开资源包文件夹"),72):0;
        int resetWidth=buttonWidth(tr("重置混搭"),52), undoWidth=buttonWidth(tr("撤销"),32);
        int revertWidth=buttonWidth(tr("还原修改"),46), applyWidth=buttonWidth(tr("应用"),35);
        int doneWidth=buttonWidth(tr("完成"),38);
        int editWidth=resetWidth+undoWidth+revertWidth+applyWidth+doneWidth+16;
        int toolsWidth=settingsWidth+setsWidth+4+(packPage?folderWidth+4:0);
        boolean splitFooter=previewReturn==null&&toolsWidth+editWidth+10>contentWidth;
        int toolsY=splitFooter?footer-UiDraw.CONTROL-4:footer;
        gridTop=packPage?toolbarY+UiDraw.CONTROL+19:target!=null||skyScenePreview()?detailY+UiDraw.CONTROL+4:detailY;
        gridBottom=toolsY-18;
        if(packPage){packPane.layout(contentLeft,gridTop,contentWidth,gridBottom-gridTop,query);cards=List.of();visible=List.of();}
        else addRenderableWidget(new VerticalScrollbar(contentLeft+contentWidth-9,gridTop,gridBottom-gridTop,()->scroll,()->((visible.size()+columns-1)/columns)*rowHeight,()->gridBottom-gridTop,rowHeight*.65,value->{scroll=scrollTarget=value;refreshCards();}));
        statusView=addRenderableWidget(new FlatButton(contentLeft,gridBottom+3,contentWidth,12,Component.empty(),b->NativeCompat.setScreen(minecraft, new MessageScreen(this,tr("TextureMixer 状态"),b.getMessage().getString()))){
            @Override public void renderContents(GuiGraphicsExtractor ctx,int mx,int my,float delta){
                if(isFocused()||isHovered())UiDraw.rounded(ctx,getX()-2,getY(),getWidth()+4,getHeight(),3,0x50737373);
                ctx.text(font,UiDraw.ellipsis(font,getMessage().getString(),getWidth()),getX(),getY()+2,isHovered()?UiDraw.TEXT:UiDraw.SECONDARY,false);
            }
        });
        if(previewReturn==null){
            tool(contentLeft,toolsY,settingsWidth,"设置",b->NativeCompat.setScreen(minecraft, new MixerSettingsScreen(this)));
            tool(contentLeft+settingsWidth+4,toolsY,setsWidth,"自定义套装",b->NativeCompat.setScreen(minecraft, new TemplatesScreen(this)));
            if(packPage)tool(contentLeft+settingsWidth+setsWidth+8,toolsY,folderWidth,"打开资源包文件夹",
                    b->NativeCompat.openPackFolder(minecraft)).quiet();
            int x=contentLeft+contentWidth-editWidth;
            reset=tool(x,footer,resetWidth,"重置混搭",b->confirmReset()).quiet();x+=resetWidth+4;
            stepUndo=tool(x,footer,undoWidth,"撤销",b->undoStep()).quiet();x+=undoWidth+4;
            undo=tool(x,footer,revertWidth,"还原修改",b->{edit(tr("还原全部未应用修改"),()->draft=service.committed.copy());rebuildWidgets();}).quiet();x+=revertWidth+4;
            apply=tool(x,footer,applyWidth,"应用",b->apply(false));x+=applyWidth+4;
            done=tool(x,footer,doneWidth,"完成",b->{if(service.busy||libraryImporting())onClose();else if(draft.changesFrom(service.committed)>0||sourcesChanged||!service.committed.enabled||service.mounted==null)apply(true);else returnToParent.run();}).primary();
        }else tool(contentLeft+contentWidth-56,footer,56,"返回",b->onClose());
        int refreshWidth=buttonWidth(tr("刷新"),40);
        tool(contentLeft+contentWidth-refreshWidth,top+5,refreshWidth,"刷新",b->refreshResources()).quiet();
        scrollTarget=scroll;lastFrame=System.nanoTime();if(!packPage)refreshCards();
        if(packPage||!modelCategory())service.previews.retainPending(Set.of());
        if(packPage||modelCategory()||tab==MixerConfig.Kind.SKY||tab==MixerConfig.Kind.SOUND||tab==MixerConfig.Kind.FONT)resourcePreviews.retain(Set.of());
    }
    private int buttonWidth(String label,int minimum){return Math.max(minimum,font.width(label)+12);}
    private FlatButton tool(int x,int y,int width,String label,FlatButton.OnPress action){
        return addRenderableWidget(new FlatButton(x,y,width,UiDraw.CONTROL,Component.literal(tr(label)),action));
    }
    private String navigationLabel(int index){
        return tr(index==0?"Resourcepack":TOP_CATEGORIES.get(index-1).label);
    }
    private void addInterfaceCategories(int y){
        int x=contentLeft;
        for(var kind:List.of(MixerConfig.Kind.HUD,MixerConfig.Kind.MENU,MixerConfig.Kind.CONTAINER,MixerConfig.Kind.FONT)){
            String label=kind==MixerConfig.Kind.HUD?"HUD Elements":kind.label;
            int w=buttonWidth(tr(label),34);
            var button=tool(x,y,w,label,pressed->{
                savePage();tab=kind;target=null;stateIndex=0;loadPage();rebuildWidgets();
            }).quiet();
            button.selected=tab==kind;
            x+=w+4;
        }
    }
    private int navigationCapacity(){
        int all=0;
        for(int index=0;index<PAGE_COUNT;index++)all+=font.width(navigationLabel(index))+12;
        if(all<=contentWidth)return PAGE_COUNT;
        for(int count=PAGE_COUNT-1;count>1;count--){
            int widest=0;
            for(int start=0;start<=PAGE_COUNT-count;start++){
                int required=0;
                for(int index=start;index<start+count;index++)required+=font.width(navigationLabel(index))+12;
                widest=Math.max(widest,required);
            }
            if(widest<=contentWidth-36)return count;
        }
        return 1;
    }
    public void localeChanged(){if(!operationFailed)notice="";focusNote="";navScrolled=false;}
    private void refreshNavigation(){
        int reserved=navCapacity<PAGE_COUNT?36:0;
        int minimumWidth=0;
        for(int index=navOffset;index<navOffset+navCapacity;index++)minimumWidth+=font.width(navigationLabel(index))+12;
        int extra=Math.max(0,(contentWidth-reserved-minimumWidth)/navCapacity);
        int x=contentLeft+(reserved==0?0:18);
        for(int index=navOffset;index<Math.min(PAGE_COUNT,navOffset+navCapacity);index++){
            int pageIndex=index;String label=navigationLabel(index);
            int tabWidth=font.width(label)+12+extra;
            var button=addRenderableWidget(new FlatButton(x,navTop,tabWidth-4,UiDraw.CONTROL,Component.literal(label),b->{
                audioPreview.stop();savePage();navScrolled=false;packPage=pageIndex==0;if(!packPage)tab=TOP_CATEGORIES.get(pageIndex-1);target=null;stateIndex=0;loadPage();rebuildWidgets();
            }).quiet());
            button.selected=packPage?index==0:index>0&&TOP_CATEGORIES.get(index-1)==(tab.interfaceCategory()?MixerConfig.Kind.HUD:tab);
            button.active=previewReturn==null||index>0;
            button.setTooltip(Tooltip.create(Component.literal(label)));categoryButtons.add(button);x+=tabWidth;
        }
        if(reserved>0){
            var previous=addRenderableWidget(navigationArrow(contentLeft,false));previous.active=navOffset>0;
            var next=addRenderableWidget(navigationArrow(contentLeft+contentWidth-16,true));next.active=navOffset+navCapacity<PAGE_COUNT;
        }
    }
    private FlatButton navigationArrow(int x,boolean forward){
        var button=new FlatButton(x,navTop,16,UiDraw.CONTROL,Component.literal(forward?tr("后"):tr("前")),pressed->{
            navScrolled=true;navOffset=Math.clamp(navOffset+(forward?1:-1),0,PAGE_COUNT-navCapacity);rebuildWidgets();
        }).symbol(forward?UiDraw.Symbol.RIGHT:UiDraw.Symbol.LEFT).quiet();
        button.narrationLabel=forward?tr("下一组分类"):tr("上一组分类");button.setTooltip(Tooltip.create(Component.literal(button.narrationLabel)));return button;
    }
    private void playAudio(Catalog.Target target,String source){audioPreview.play(target.id(),source);}
    private boolean skyScenePreview(){return tab==MixerConfig.Kind.SKY&&(target==null||!target.id().equals("weather"));}
    private boolean modelCategory(){return tab==MixerConfig.Kind.ITEM||tab==MixerConfig.Kind.BLOCK;}
    public String sourceName(String id){if(id.startsWith(MixerConfig.STACK_PREFIX))return tr("主材质包叠加 · ")+draft.mainStack().size();if(id.equals(MixerConfig.VANILLA))return "Vanilla";var source=draft.sources.get(id);return source==null?tr("来源失联"):source.lastKnownFolderName().replaceAll("\\s+", " ").strip();}
    public List<String> sourceIds(){
        List<String> ids=new ArrayList<>();ids.add(MixerConfig.VANILLA);
        if(previewReturn != null){if(!previewSource.equals(MixerConfig.VANILLA))ids.add(previewSource);return ids;}
        ids.addAll(draft.mainStack());
        service.library.availableSourceIds(draft).stream().filter(draft.candidateSources::contains).filter(id->!ids.contains(id)).forEach(ids::add);
        return ids;
    }
    public List<String> registeredIds(){
        List<String> ids=new ArrayList<>();ids.add(MixerConfig.VANILLA);
        ids.addAll(service.library.availableSourceIds(draft));
        return ids;
    }
    public void sourceChanged(){sourcesChanged=true;service.previews.invalidate();skyPreviews.invalidateCustom();resourcePreviews.invalidateCustom();notice=tr("来源已更新，应用后生效");}
    public void refreshCards(){
        if(packPage||search==null||service.catalog==null)return;
        for(Card card:cards)removeWidget(card);List<Object> result=new ArrayList<>();
        if(target==null){for(Catalog.Target entry:service.catalog.list(tab))if(entry.matches(query))result.add(entry);}
        else for(String id:sourceIds())if(SearchQuery.matches(sourceName(id),query))result.add(id);
        visible=List.copyOf(result);int rows=(visible.size()+columns-1)/columns;
        scroll=Math.clamp(scroll,0,Math.max(0,rows*rowHeight-(gridBottom-gridTop)));
        scrollTarget=Math.clamp(scrollTarget,0,Math.max(0,rows*rowHeight-(gridBottom-gridTop)));
        int first=Math.max(0,(int)scroll/rowHeight),last=Math.min(rows,((int)scroll+gridBottom-gridTop)/rowHeight+2);
        List<Card> widgets=new ArrayList<>();for(int row=first;row<last;row++)for(int col=0;col<columns;col++){
            int index=row*columns+col;if(index>=visible.size())break;
            Object value=visible.get(index);Catalog.Target cardTarget=target==null?(Catalog.Target)value:target;
            String source=target==null?(previewReturn == null ? draft.source(tab,cardTarget.id()) : previewSource):(String)value;
            int gridWidth=columns*(tile+9)-9;int x=contentLeft+(contentWidth-9-gridWidth)/2+col*(tile+9),y=gridTop+row*rowHeight-(int)scroll;
            Card card=new Card(x,y,tile,rowHeight-6,cardTarget,source,target==null);
            widgets.add(addRenderableWidget(card));
        }cards=List.copyOf(widgets);lastRows=first;dirtyCount=draft.changesFrom(service.committed);
        if(modelCategory()){
            Set<String> pending=new HashSet<>();
            for(Card card:cards){
                var selectedMode=card.overview?PreviewCache.Mode.ICON:mode;
                pending.add(PreviewCache.key(card.entry,card.source,tab==MixerConfig.Kind.BLOCK?stateIndex:0,selectedMode));
                if(service.preloadEnabled()&&card.getY()<gridBottom&&card.getBottom()>gridTop)
                    service.previews.get(card.entry,card.source,tab==MixerConfig.Kind.BLOCK?stateIndex:0,selectedMode);
            }
            service.previews.retainPending(pending);
        }else if(tab!=MixerConfig.Kind.SKY&&tab!=MixerConfig.Kind.SOUND&&tab!=MixerConfig.Kind.FONT){
            Set<String> pinned=new HashSet<>();
            for(Card card:cards)pinned.add(ResourcePreviewCache.key(card.entry,card.source));
            resourcePreviews.retain(pinned);
            for(Card card:cards){
                if(card.getY()<gridBottom&&card.getBottom()>gridTop)resourcePreviews.get(card.entry,card.source);
                else resourcePreviews.prefetch(card.entry,card.source);
            }
        }
    }
    private void back(){Catalog.Target previous=target;savePage();target=null;loadPage();rebuildWidgets();int index=visible.indexOf(previous);if(index>=0)focusCard(index);}
    private void open(Catalog.Target selected){savePage();target=selected;stateIndex=0;mode=PreviewCache.Mode.ICON;itemVariant=ItemPreviewState.NORMAL;loadPage();rebuildWidgets();}
    private SkyScene.Context skyContext(){return new SkyScene.Context(previewTime,previewDay,weather>0?1:0,weather>1?1:0,previewBiome,previewHeight);}
    String timeName(){return previewTime==6000?tr("Day"):previewTime==12000?tr("Sunset"):previewTime==18000?tr("Night"):tr("Time ")+previewTime;}
    String weatherName(){return new String[]{tr("Clear"),tr("Rain"),tr("Thunder")}[weather];}
    String skySummary(){return timeName()+" · "+weatherName()+" · "+previewBiome.replace("minecraft:","")+" · Y "+(int)previewHeight+tr(" · Day ")+previewDay;}
    void cycleSkyTime(){previewTime=previewTime==6000?12000:previewTime==12000?18000:6000;}
    void cycleSkyWeather(){weather=(weather+1)%3;}
    void matchSky(){
        String dimension=target==null?"overworld":target.id();String source=previewReturn==null?draft.source(MixerConfig.Kind.SKY,dimension):previewSource;
        var entry=skyPreviews.get(dimension,source,skyContext());
        if(entry.scene==null||entry.scene.layers().isEmpty()){notice=entry.error!=null?entry.error:tr("当前来源没有自定义天空层");return;}
        var layer=entry.scene.layers().getFirst();
        if(!layer.days().isEmpty())previewDay=layer.days().getFirst().min();
        if(!layer.heights().isEmpty())previewHeight=layer.heights().getFirst().min();
        if(!layer.biomes().isEmpty()){
            if(!layer.excludeBiomes())previewBiome=layer.biomes().iterator().next();
            else for(String biome:List.of("minecraft:plains","minecraft:desert","minecraft:forest","minecraft:the_void"))if(!layer.biomes().contains(biome)){previewBiome=biome;break;}
        }
        if(!layer.weather().isEmpty())weather=layer.weather().contains("clear")?0:layer.weather().contains("rain")?1:2;
        if(layer.timeAlpha(previewTime)<=0)for(int time=0;time<24000;time+=100)if(layer.timeAlpha(time)>.5){previewTime=time;break;}
        notice=tr("预览条件已匹配：")+skySummary()+tr("；未修改世界");rebuildWidgets();
    }
    public void chooseFolder(boolean main) { chooseFolder(main, this); }
    public void chooseFolder(boolean main, Screen returnTo) {
        if (picking || service.busy || importingFolder) return;
        picking = true;
        operationFailed = false;
        notice = tr("请选择 ZIP 材质包…");
        long owner = NativeCompat.windowOwner(minecraft);
        CompletableFuture.supplyAsync(() -> {
            try { return PackFileDialog.open(main, owner); }
            catch (Throwable error) { throw new CompletionException(error); }
        }, PackFileDialog.windows() ? minecraft : service.workers).whenCompleteAsync((path, error) -> {
            picking = false;
            if (error != null) {
                NativeCompat.setScreen(minecraft, new PathScreen(returnTo, value -> addFolder(value, main, returnTo)));
            } else if (path == null) {
                notice = tr("已取消添加");
            } else addFolder(Path.of(path), main, returnTo);
        }, minecraft);
    }
    public void chooseLibraryFolder(Screen returnTo) {
        if (picking || service.busy || importingFolder) return;
        picking = true;
        long owner = NativeCompat.windowOwner(minecraft);
        CompletableFuture.supplyAsync(() -> {
            try { return FolderFileDialog.open(owner); }
            catch (Throwable error) { throw new CompletionException(error); }
        }, PackFileDialog.windows() ? minecraft : service.workers).whenCompleteAsync((path, error) -> {
            picking = false;
            if (error != null) NativeCompat.setScreen(minecraft, new PathScreen(returnTo, true, value -> importLibraryFolder(value, returnTo)));
            else if (path != null) importLibraryFolder(Path.of(path), returnTo);
        }, minecraft);
    }
    public void importLibraryFolder(Path path, Screen returnTo) {
        if (importingFolder || service.busy) return;
        importingFolder = true;
        notice = tr("正在识别文件夹中的材质包…");
        service.library.importFolder(path, draft).whenCompleteAsync((result, error) -> {
            importingFolder = false;
            operationFailed = error != null;
            if (error != null) notice = MixerService.message(error);
            else {
                notice = tr("已导入 ") + result.imported() + tr(" 个材质包");
                if (!result.failures().isEmpty()) {
                    notice += "，" + result.failures().size() + tr(" 个未导入");
                    for (var failure : result.failures()) notice += "\n" + plainName(failure.path()) + "\n" + failure.message();
                }
                service.previews.invalidate();
                skyPreviews.invalidateCustom();
            }
            if (NativeCompat.screen(minecraft) == returnTo || NativeCompat.screen(minecraft) == this)
                NativeCompat.setScreen(minecraft, new SourceManagerScreen(this, error == null ? result.group().id() : null, notice));
        }, minecraft);
    }
    private void addFolder(Path path, boolean main, Screen returnTo) {
        if (!Files.isRegularFile(path) || !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            operationFailed = true;
            notice = tr("请选择 ZIP 材质包文件");
            NativeCompat.setScreen(minecraft, this);
            return;
        }
        notice = tr("正在读取 ZIP 材质包…");
        service.add(path, draft, false).whenCompleteAsync((id, error) -> {
            operationFailed = error != null;
            notice = error == null ? tr("已添加 ") + sourceName(id) : MixerService.message(error);
            if (error == null) {
                sourcesChanged = true;
                service.previews.invalidate();
                skyPreviews.invalidateCustom();
                try { service.library.markStandalone(id); }
                catch (java.io.IOException failure) { notice += tr("，单独登记失败 ") + failure.getMessage(); }
            }
            if(error==null&&main){previewMain(id,this);return;}
            if (returnTo instanceof SourceManagerScreen) NativeCompat.setScreen(minecraft, new SourceManagerScreen(this, null, notice));
            else { NativeCompat.setScreen(minecraft, this); rebuildWidgets(); }
        }, minecraft);
    }
    private void apply(boolean close){operationFailed=false;service.apply(draft).whenCompleteAsync((v,error)->{
        operationFailed=error!=null;notice=service.status;if(error==null){draft=service.committed.copy();sourcesChanged=false;if(close&&NativeCompat.screen(minecraft)==this){returnToParent.run();return;}}if(NativeCompat.screen(minecraft)==this)rebuildWidgets();
    },minecraft);}
    @Override public void tick(){
        if(service.preloadEnabled()&&!libraryImporting()){
            skyPreviews.warm(draft,previewSource,skyContext());
            if(!packPage&&tab==MixerConfig.Kind.SKY&&target==null)
                for(String dimension:List.of("overworld","end","weather"))skyPreviews.warmVanilla(dimension,skyContext());
        }
        if(itemStateButton!=null){
            itemStateButton.visible=itemStates(target,null).size()>1;
            itemStateButton.active=minecraft.level!=null;
            itemStateButton.setTooltip(Tooltip.create(Component.literal(minecraft.level==null?tr("进入世界后可预览附魔条件外观"):tr("当前预览 ")+itemStateName()+tr("，点击切换附魔条件"))));
        }
        if(waitingForCatalog&&service.catalog!=null){waitingForCatalog=false;rebuildWidgets();}
        if(previewReturn==null && service.catalog!=null && !service.busy && !importingFolder && !discovering
                && System.nanoTime()>=nextDiscovery) discoverResources();
        dirtyCount=draft.changesFrom(service.committed);
        if(reset!=null)reset.active=!service.busy&&!libraryImporting();
        if(stepUndo!=null)stepUndo.active=!service.busy&&!libraryImporting()&&!history.isEmpty();
        if(apply!=null){apply.active=service.catalog!=null&&!service.busy&&!libraryImporting()&&(dirtyCount>0||sourcesChanged||!service.committed.enabled||service.mounted==null);undo.active=!service.busy&&!libraryImporting()&&dirtyCount>0;done.active=true;done.setMessage(Component.literal(service.busy||libraryImporting()?tr("返回"):tr("完成")));}
    }
    private void refreshResources() {
        if (service.busy || libraryImporting()) return;
        service.refreshSources().whenCompleteAsync((ignored,error)->{
            if(error!=null){operationFailed=true;notice=MixerService.message(error);return;}
            sourceChanged();
            nextDiscovery=0;
            discoverResources();
        },minecraft);
    }
    private void discoverResources() {
        discovering=true;
        boolean first=!discovered;
        if(first){notice=tr("正在自动识别资源包…");minecraft.getResourcePackRepository().reload();}
        service.library.discoverResourcePacks().whenCompleteAsync((result,error)->{
            discovering=false;
            nextDiscovery=System.nanoTime()+2_000_000_000L;
            if(error!=null){operationFailed=true;notice=MixerService.message(error);return;}
            draft.sources.putAll(result.sources());
            result.sources().keySet().forEach(draft::addCandidate);
            if(first&&!service.committed.enabled&&draft.mainStack().isEmpty()&&history.isEmpty()) inheritEnabledPacks();
            discovered=true;
            if(result.changed()||first){
                if(result.changed())sourceChanged();
                notice=tr("已自动识别资源包");
                if(!result.failures().isEmpty())notice+="，"+result.failures().size()+tr(" 个包读取失败");
                if(NativeCompat.screen(minecraft)==this)rebuildWidgets();
            }
        },minecraft);
    }
    private void inheritEnabledPacks() {
        List<String> enabled=new ArrayList<>();
        Path root=minecraft.getResourcePackDirectory().toAbsolutePath().normalize();
        for(var profile:minecraft.getResourcePackRepository().getSelectedPacks()){
            if(!profile.getId().startsWith("file/"))continue;
            Path path=root.resolve(profile.getId().substring(5)).normalize();
            draft.sources.values().stream().filter(source->Path.of(source.canonicalRoot()).equals(path))
                    .filter(source->service.sources.containsKey(source.id()))
                    .findFirst().ifPresent(source->enabled.addFirst(source.id()));
        }
        if(!enabled.isEmpty())draft.setMainStack(enabled);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor context,int mouseX,int mouseY,float delta){
        EnchantPreview.clearQueued();
        resourcePreviews.beginFrame();
        UiDraw.window(context,left,top,panelWidth,panelHeight);
        long now=System.nanoTime();double dt=Math.min(.05,(now-lastFrame)/1e9);lastFrame=now;
        if(!suspended&&!packPage&&Math.abs(scroll-scrollTarget)>.05){
            scroll+= (scrollTarget-scroll)*(1-Math.exp(-22*dt));if(Math.abs(scroll-scrollTarget)<.2)scroll=scrollTarget;
            int first=(int)scroll/rowHeight;if(first!=lastRows)refreshCards();
            else for(Card card:cards){int index=visible.indexOf(card.overview?card.entry:card.source);card.setY(gridTop+index/columns*rowHeight-(int)scroll);}
        }
        String heading=previewReturn!=null?tr("预览 · ")+sourceName(previewSource):target==null?"TextureMixer":target.name();
        context.text(font,UiDraw.ellipsis(font,heading,contentWidth-70),contentLeft,top+10,UiDraw.TEXT,false);
        context.text(font,tr("Search"),contentLeft,search.getY(),UiDraw.SECONDARY,false);
        if(packPage)packPane.draw(context);
        boolean operationStatus=service.busy||operationFailed;
        String line=service.busy?service.status:operationFailed?notice:!focusNote.isBlank()?focusNote:!notice.isBlank()?notice:service.status;
        if(!operationStatus)for(Card card:cards)if((card.isHovered()||card.isFocused())&&card.getY()<gridBottom&&card.getY()+card.getHeight()>gridTop&&!card.description.isBlank()){line=sourceName(card.source)+" · "+card.description;break;}
        if(tab==MixerConfig.Kind.SOUND&&!packPage&&!audioPreview.status.isBlank()&&!operationStatus)line=audioPreview.status;
        statusView.setMessage(Component.literal(line));statusView.setTooltip(Tooltip.create(Component.literal(line+tr("\n点击查看完整状态"))));
        super.extractRenderState(context,mouseX,mouseY,delta);
        context.fill(contentLeft,gridBottom+1,contentLeft+contentWidth,gridBottom+2,UiDraw.DIVIDER);
        if(!packPage&&visible.isEmpty())context.centeredText(font,service.catalog==null?tr("正在准备资源…"):tr("没有匹配结果"),contentLeft+contentWidth/2,gridTop+18,0xffa0a0a0);
        drawUndoToast(context);focusNote="";
    }
    private void drawUndoToast(GuiGraphicsExtractor context){
        if(undoToast.isEmpty())return;
        double age=(System.nanoTime()-undoToastStarted)/1e9;
        double fade=Math.clamp(Math.min(age/.18,(3.2-age)/.4),0,1);fade=fade*fade*(3-2*fade);
        int alpha=(int)(255*fade);if(alpha<5)return;
        int w=Math.min(contentWidth,font.width(undoToast)+16),x=contentLeft+contentWidth-w,y=gridBottom-24;
        UiDraw.rounded(context,x,y,w,20,4,(alpha<<24)|0x424242);
        context.text(font,UiDraw.ellipsis(font,undoToast,w-16),x+8,y+6,(alpha<<24)|0xffffff,false);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(my>=navTop&&my<navBottom){navScrolled=true;navOffset=Math.clamp(navOffset-(int)Math.signum(vertical),0,PAGE_COUNT-navCapacity);rebuildWidgets();return true;}
        if(packPage)return packPane.scroll(mx,my,vertical);
        if(mx>=contentLeft&&mx<=left+panelWidth&&my>=gridTop&&my<gridBottom){int rows=(visible.size()+columns-1)/columns;scrollTarget=Math.clamp(scrollTarget-vertical*rowHeight*.65,0,Math.max(0,rows*rowHeight-(gridBottom-gridTop)));return true;}return true;
    }
    @Override protected boolean mixerClick(double mouseX,double mouseY,int button,java.util.function.BooleanSupplier vanilla){
        boolean clearing=clearSearch!=null&&clearSearch.active&&clearSearch.isMouseOver(mouseX,mouseY);
        boolean handled=vanilla.getAsBoolean();
        if(handled&&packPage&&packPane.restoreFocus())return true;
        // ParentElement assigns the clicked child after onPress; transfer focus only after that assignment.
        if(handled&&clearing){setFocused(search);search.setFocused(true);}
        else if(handled&&getFocused()!=null&&!children().contains(getFocused())){
            setFocused(categoryButtons.stream().filter(candidate->candidate.selected).findFirst().orElse(null));
        }
        return handled;
    }
    @Override protected boolean mixerKey(int key,int modifiers,java.util.function.BooleanSupplier vanilla){
        if(key==InputConstants.KEY_F&&(modifiers&InputConstants.MOD_CONTROL)!=0){setFocused(search);search.setFocused(true);return true;}
        if(getFocused() instanceof Card card&&Set.of(InputConstants.KEY_LEFT,InputConstants.KEY_RIGHT,InputConstants.KEY_UP,InputConstants.KEY_DOWN).contains(key)){
            int index=visible.indexOf(card.overview?card.entry:card.source);
            int offset=switch(key){case InputConstants.KEY_LEFT->-1;case InputConstants.KEY_RIGHT->1;case InputConstants.KEY_UP->-columns;default->columns;};
            focusCard(Math.clamp(index+offset,0,visible.size()-1));return true;
        }
        boolean handled=vanilla.getAsBoolean();
        if(packPage)packPane.restoreFocus();
        if(getFocused() instanceof Card card){int index=visible.indexOf(card.overview?card.entry:card.source);if(index>=0&&(card.getY()<gridTop||card.getY()+card.getHeight()>gridBottom))focusCard(index);}
        return handled;
    }
    private void focusCard(int index){
        if(index<0||index>=visible.size())return;int y=index/columns*rowHeight;
        if(y<scroll)scroll=y;else if(y+rowHeight>scroll+gridBottom-gridTop)scroll=y+rowHeight-(gridBottom-gridTop);
        scrollTarget=scroll;Object key=visible.get(index);refreshCards();for(Card card:cards)if(Objects.equals(key,card.overview?card.entry:card.source)){setFocused(card);card.setFocused(true);break;}
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){if(!suspended){resourcePreviews.retain(Set.of());releaseDialogPreview();audioPreview.close();packPane.close();}}
    @Override public void onClose(){
        if (previewReturn != null) {
            if (target != null) { back(); return; }
            NativeCompat.setScreen(minecraft, previewReturn);return;
        }
        if(service.busy){returnToParent.run();return;}
        if(target!=null){back();return;}
        if(draft.changesFrom(service.committed)>0){NativeCompat.setScreen(minecraft, new ChoiceScreen(this,tr("尚有未应用修改"),
                new ChoiceScreen.Choice(tr("应用并关闭"),()->{NativeCompat.setScreen(minecraft, this);apply(true);}),
                new ChoiceScreen.Choice(tr("放弃修改"),returnToParent),
                new ChoiceScreen.Choice(tr("继续编辑"),()->NativeCompat.setScreen(minecraft, this))));}
        else{returnToParent.run();}
    }
    private final class Card extends FlatButton {
        private final Catalog.Target entry;private final String source;private final boolean overview;
        private String description="";
        private final UiDraw.Motion hoverMotion=new UiDraw.Motion();
        Card(int x,int y,int w,int h,Catalog.Target entry,String source,boolean overview){
            super(x,y,w,h,net.minecraft.network.chat.Component.literal(overview?entry.name():sourceName(source)),b->{});this.entry=entry;this.source=source;this.overview=overview;
            selected=previewReturn == null&&!overview&&source.equals(draft.source(tab,entry.id()));
            String path=source.startsWith(MixerConfig.STACK_PREFIX)?tr("按主材质包顺序叠加"):source.equals(MixerConfig.VANILLA)?tr("当前版本内置原版"):plainName(draft.sources.get(source).canonicalRoot());
            setTooltip(Tooltip.create(net.minecraft.network.chat.Component.literal((overview?entry.name()+"\n"+entry.id():sourceName(source)+"\n"+path))));
        }
        @Override protected boolean mixerClick(double mouseX,double mouseY,int button,java.util.function.BooleanSupplier vanilla){
            if(mouseY<gridTop||mouseY>=gridBottom)return false;
            return vanilla.getAsBoolean();
        }
        @Override boolean tooltipHovered(int x,int y){return y>=gridTop&&y<gridBottom&&super.tooltipHovered(x,y);}
        @Override protected void mixerPress(Runnable vanilla){
            if(!active||service.busy)return;
            if(overview&&previewReturn!=null)previewChoice(entry,source);
            else if(overview)open(entry);
            else if(previewReturn==null)previewChoice(entry,source);
            else if(tab==MixerConfig.Kind.SOUND)playAudio(entry,source);
        }
        @Override protected net.minecraft.network.chat.MutableComponent createNarrationMessage(){return super.createNarrationMessage().append(net.minecraft.network.chat.Component.literal(description.isBlank()?"":"，"+description));}
        @Override public void renderContents(GuiGraphicsExtractor ctx,int mx,int my,float delta){
            if(getY()+getHeight()<gridTop||getY()>=gridBottom)return;
            ScreenRectangle clip=new ScreenRectangle(contentLeft,gridTop,contentWidth,gridBottom-gridTop);
            ctx.enableScissor(clip.left(),clip.top(),clip.right(),clip.bottom());
            UiDraw.rounded(ctx,getX(),getY(),tile,tile,4,UiDraw.DETAIL);
            boolean chosen=previewReturn == null&&!overview&&source.equals(draft.source(tab,entry.id()));
            selected=chosen;
            String note="";boolean fallback=false;
            if(tab==MixerConfig.Kind.SKY){
                var preview=suspended?skyPreviews.getIfPresent(entry.id(),source,skyContext()):skyPreviews.get(entry.id(),source,skyContext());
                if(preview!=null){
                    skyPreviews.draw(preview,ctx,getX(),getY(),tile);
                    note=preview.error;active=overview||preview.error==null;fallback=preview.scene!=null&&!preview.scene.fromSource();
                    if(note==null&&preview.scene!=null&&!preview.scene.diagnostics().isEmpty())note=String.join("；",preview.scene.diagnostics());
                    if(note==null&&preview.scene!=null&&preview.scene.layers().stream().anyMatch(layer->layer.alpha(skyContext())<=0))note=tr("部分图层当前预览条件不匹配；可用「匹配条件」查看");
                }
                String paused=entry.id().equals("weather")?"":com.fullyx.texturemixer.sky.SkySceneRenderer.pausedReason();if(!paused.isEmpty())note=paused+tr("；预览仍可浏览");
                if(preview==null||preview.texture==null)placeholder(ctx,preview!=null&&preview.error!=null);
            }else if(tab==MixerConfig.Kind.SOUND){
                int cx=getX()+tile/2,cy=getY()+tile/2;
                for(int i=0;i<12;i++)ctx.fill(cx-4+i,cy-10+i,cx-3+i,cy+11-i,0xffa0a0a0);
                note=overview?tr("Open sound event"):tr("点击试听，应用后生效");
            }else if(tab==MixerConfig.Kind.FONT){
                String sample="Aa";
                ctx.text(font,sample,getX()+(tile-font.width(sample))/2,getY()+tile/2-4,UiDraw.TEXT,false);
            }else if(!modelCategory()){
                var preview=suspended?resourcePreviews.getIfPresent(entry,source):resourcePreviews.get(entry,source);
                if(preview!=null){
                    resourcePreviews.drawOverview(preview,entry,source,!suspended,ctx,getX(),getY(),tile);
                    fallback=preview.texture!=null&&!preview.custom;
                    note=preview.error;active=overview||preview.error==null;
                    if(preview.empty)note=tr("此原生粒子没有贴图帧，外观由游戏代码生成");
                    if(!preview.empty&&resourcePreviews.needsReference(preview)&&!source.equals(MixerConfig.VANILLA))
                        note=tr(tab==MixerConfig.Kind.PARTICLE?"当前来源隐藏此粒子，主页显示原版参照图":"当前材质完全透明，可参照原版外观");
                }
                if(preview==null||preview.texture==null)placeholder(ctx,preview!=null&&preview.error!=null);
            }else{
                var preview=suspended?service.previews.getIfPresent(entry,source,tab==MixerConfig.Kind.BLOCK?stateIndex:0,overview?PreviewCache.Mode.ICON:mode)
                        :service.previews.get(entry,source,tab==MixerConfig.Kind.BLOCK?stateIndex:0,overview?PreviewCache.Mode.ICON:mode);
                if(preview!=null&&preview.ready())preview.arena.draw(ctx,entry,overview?PreviewCache.Mode.ICON:mode,getX(),getY(),tile,clip,overview?ItemPreviewState.NORMAL:itemVariant);
                else placeholder(ctx,preview!=null&&preview.error!=null);
                note=preview==null?null:preview.error == null ? preview.note : preview.error;active=overview||preview==null||preview.error==null||preview.budgetLimited;
                fallback=preview!=null&&preview.status==GraphCompiler.Status.VANILLA_FALLBACK;
                if(!overview&&mode==PreviewCache.Mode.WORN&&minecraft.level==null){
                    ctx.centeredText(font,tr("进入世界"),getX()+tile/2,getY()+tile/2-4,0xffa0a0a0);
                    note=tr("进入世界后可查看穿戴预览");
                }
                if(tab==MixerConfig.Kind.BLOCK&&entry.blockState().getRenderShape()==net.minecraft.world.level.block.RenderShape.INVISIBLE&&entry.blockState().getFluidState().isEmpty()){
                    int x=getX()+tile/4,y=getY()+tile/4,s=tile/2;
                    for(int i=0;i<s;i+=6){ctx.fill(x+i,y,x+Math.min(i+3,s),y+1,0xff808080);ctx.fill(x+i,y+s,x+Math.min(i+3,s),y+s+1,0xff808080);ctx.fill(x,y+i,x+1,y+Math.min(i+3,s),0xff808080);ctx.fill(x+s,y+i,x+s+1,y+Math.min(i+3,s),0xff808080);}
                    ctx.centeredText(font,tr("Invisible"),getX()+tile/2,getY()+tile/2-4,0xffa0a0a0);
                    if(note==null)note=tr("不可见技术方块：虚线仅为示意；不改变世界可见规则或碰撞");
                }
            }
            if(fallback&&!source.equals(MixerConfig.VANILLA)){ctx.fill(getX()+4,getY()+4,getX()+10,getY()+6,0xffffff55);ctx.fill(getX()+4,getY()+4,getX()+6,getY()+11,0xffffff55);}
            if(chosen){ctx.fill(getX()+tile-12,getY()+5,getX()+tile-9,getY()+10,0xffffffff);ctx.fill(getX()+tile-9,getY()+8,getX()+tile-5,getY()+10,0xffffffff);}
            float hover=hoverMotion.value(isHovered()||isFocused()?1:0,120);
            if(hover>.01f||chosen){int color=isFocused()||chosen?UiDraw.FOCUS:UiDraw.mix(0xff333333,0xff8a8a8a,hover);
                UiDraw.outline(ctx,getX(),getY(),tile,tile,color);}
            net.minecraft.network.chat.Component labelText=overview?net.minecraft.network.chat.Component.literal(entry.name().replaceFirst("^(Container|HUD) / ","")):getMessage();
            var lines=font.split(labelText,tile);
            for(int i=0;i<Math.min(2,lines.size());i++){
                var label=i==1&&lines.size()>2?UiDraw.ellipsis(font,lines.get(i),tile):lines.get(i);
                ctx.text(font,label,getX()+(tile-font.width(label))/2,getY()+tile+5+i*10,0xffffffff,false);
            }
            String path=source.startsWith(MixerConfig.STACK_PREFIX)?tr("按主材质包顺序叠加"):source.equals(MixerConfig.VANILLA)?tr("当前版本内置原版"):plainName(draft.sources.get(source).canonicalRoot());
            String sourceError=service.sourceErrors.get(source);if(sourceError!=null)note=sourceError+(note==null?"":"；"+note);
            if(note==null&&fallback&&!source.equals(MixerConfig.VANILLA))note=tr("未提供相关资源，使用原版");
            description=(chosen?tr("当前选择；"):"")+(note==null?"":note);
            setTooltip(Tooltip.create(net.minecraft.network.chat.Component.literal((overview?entry.name()+"\n"+entry.id():sourceName(source)+"\n"+path)+(description.isBlank()?"":"\n"+description))));
            if(isHovered()||isFocused())focusNote=note!=null?sourceName(source)+": "+note:fallback&&!source.equals(MixerConfig.VANILLA)?sourceName(source)+tr(" 未提供相关资源，使用原版"):entry.name()+" · "+sourceName(source);
            ctx.disableScissor();
        }
        private void placeholder(GuiGraphicsExtractor ctx,boolean error){int color=error?0xffffff55:0xff808080;int size=error?12:6;
            ctx.fill(getX()+tile/2-size/2,getY()+tile/2-1,getX()+tile/2+size/2,getY()+tile/2+1,color);}
    }
}
