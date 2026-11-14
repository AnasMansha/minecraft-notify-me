package com.notifyme.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

import org.lwjgl.input.Mouse;

/** Draggable slider that snaps to a fixed list of values (e.g. 0s, 5s, 1m ...). */
public class StepSlider extends GuiButton {

    private final int[] steps;
    private final String prefix;
    private final int accent;
    private int index;
    private boolean dragging;
    public String disabledText = "";

    public StepSlider(int id, int x, int y, int w, int h, String prefix, int[] steps, int value, int accent) {
        super(id, x, y, w, h, "");
        this.steps = steps;
        this.prefix = prefix;
        this.accent = accent;
        setValue(value);
    }

    public int getValue() {
        return steps[index];
    }

    public void setValue(int v) {
        index = 0;
        for (int i = 0; i < steps.length; i++) if (steps[i] <= v) index = i;
    }

    private void setFromMouse(int mx) {
        float f = (mx - xPosition - 3) / (float) (width - 6);
        f = Math.max(0f, Math.min(1f, f));
        index = Math.round(f * (steps.length - 1));
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mx, int my) {
        if (enabled && visible
            && mx >= xPosition
            && my >= yPosition
            && mx < xPosition + width
            && my < yPosition + height) {
            setFromMouse(mx);
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(int mx, int my) {
        dragging = false;
    }

    @Override
    public void drawButton(Minecraft mc, int mx, int my) {
        if (!visible) return;
        if (dragging) {
            if (Mouse.isButtonDown(0)) setFromMouse(mx);
            else dragging = false;
        }
        boolean hover = enabled && mx >= xPosition
            && my >= yPosition
            && mx < xPosition + width
            && my < yPosition + height;
        int x0 = xPosition, y0 = yPosition, x1 = xPosition + width, y1 = yPosition + height;
        Gui.drawRect(x0, y0, x1, y1, hover || dragging ? 0xFF59616B : 0xFF3A4048);
        Gui.drawRect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFF181A1E);
        if (enabled) {
            float f = steps.length <= 1 ? 0 : index / (float) (steps.length - 1);
            int fill = x0 + 1 + (int) ((width - 2) * f);
            // filled part glows like powered redstone dust
            Gui.drawRect(x0 + 1, y0 + 1, fill, y1 - 1, FlatButton.mix(accent, 0xFF181A1E, 0.45f));
            int kx = Math.max(x0 + 1, Math.min(x1 - 5, fill - 2));
            Gui.drawRect(kx, y0, kx + 4, y1, accent);
            Gui.drawRect(kx + 1, y0 + 1, kx + 3, y0 + 2, 0x55FFFFFF);
        }
        String label = enabled ? prefix + fmt(getValue()) : disabledText;
        mc.fontRenderer.drawString(
            label,
            x0 + (width - mc.fontRenderer.getStringWidth(label)) / 2,
            y0 + (height - 8) / 2 + 1,
            enabled ? 0xFFFFFFFF : 0xFF6A7078,
            true);
    }

    public static String fmt(int sec) {
        if (sec == 0) return "none";
        if (sec < 60) return sec + "s";
        if (sec < 3600) return (sec / 60) + "m";
        return (sec / 3600) + "h";
    }
}
