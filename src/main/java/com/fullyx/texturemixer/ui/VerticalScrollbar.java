package com.fullyx.texturemixer.ui;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** A native focusable scrollbar with a wider hit area around its narrow painted track. */
final class VerticalScrollbar extends FlatButton {
    private final DoubleSupplier position, content, viewport;
    private final DoubleConsumer change;
    private final double step;
    private double grabOffset;
    private boolean dragging;

    VerticalScrollbar(int x, int y, int height, DoubleSupplier position,
                      DoubleSupplier content, DoubleSupplier viewport, double step, DoubleConsumer change) {
        super(x, y, 10, height, net.minecraft.text.Text.literal(tr("垂直滚动条")), button -> {});
        this.position = position;
        this.content = content;
        this.viewport = viewport;
        this.change = change;
        this.step = step;
    }
    private double maximum() { return Math.max(0, content.getAsDouble() - viewport.getAsDouble()); }
    private int thumbHeight() {
        return Math.min(getHeight(), Math.max(18, (int)(getHeight() * viewport.getAsDouble() / Math.max(1, content.getAsDouble()))));
    }
    private double thumbTop() {
        return getY() + Math.clamp(position.getAsDouble() / Math.max(1, maximum()), 0, 1) * (getHeight() - thumbHeight());
    }
    private void seek(double y) {
        double travel = getHeight() - thumbHeight();
        change.accept(travel <= 0 ? 0 : Math.clamp((y - getY() - grabOffset) / travel, 0, 1) * maximum());
    }
    @Override public void drawIcon(DrawContext context, int mx, int my, float delta) {
        active = maximum() > 0;
        if (!active) return;
        UiDraw.rounded(context,getX()+4,getY(),2,getHeight(),1,0x90535353);
        int y=(int)thumbTop(),color=dragging||isFocused()?UiDraw.FOCUS:isHovered()?0xffb4b4b4:0xff929292;
        UiDraw.rounded(context,getX()+3,y,4,thumbHeight(),2,color);
    }
    @Override protected boolean mixerClick(double mouseX,double mouseY,int button,java.util.function.BooleanSupplier vanilla) {
        if (button != 0 || maximum() <= 0 || !isMouseOver(mouseX, mouseY)) return false;
        double top = thumbTop();
        grabOffset = mouseY >= top && mouseY <= top + thumbHeight() ? mouseY - top : thumbHeight() / 2.0;
        dragging = true;
        seek(mouseY);
        return true;
    }
    @Override protected boolean mixerDrag(double mouseX,double mouseY,int button,java.util.function.BooleanSupplier vanilla) {
        if (!dragging || button != 0) return false;
        seek(mouseY);
        return true;
    }
    @Override protected boolean mixerRelease(java.util.function.BooleanSupplier vanilla) {
        boolean handled = dragging;
        dragging = false;
        return handled;
    }
    @Override protected boolean mixerKey(int key,int modifiers,java.util.function.BooleanSupplier vanilla) {
        double next = switch (key) {
            case GLFW.GLFW_KEY_UP -> position.getAsDouble() - step;
            case GLFW.GLFW_KEY_DOWN -> position.getAsDouble() + step;
            case GLFW.GLFW_KEY_PAGE_UP -> position.getAsDouble() - viewport.getAsDouble();
            case GLFW.GLFW_KEY_PAGE_DOWN -> position.getAsDouble() + viewport.getAsDouble();
            case GLFW.GLFW_KEY_HOME -> 0;
            case GLFW.GLFW_KEY_END -> maximum();
            default -> Double.NaN;
        };
        if (Double.isNaN(next)) return vanilla.getAsBoolean();
        change.accept(Math.clamp(next, 0, maximum()));
        return true;
    }
}
