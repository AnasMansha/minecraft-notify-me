package com.notifyme.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

/** Flat dark button / selectable chip in the notifier GUI style. */
public class FlatButton extends GuiButton {

    /** Accent colour (ARGB) used for border + text when selected, and on hover. */
    public int accent;
    public boolean selected;

    public FlatButton(int id, int x, int y, int w, int h, String text, int accent) {
        super(id, x, y, w, h, text);
        this.accent = accent;
    }

    @Override
    public void drawButton(Minecraft mc, int mx, int my) {
        if (!visible) return;
        field_146123_n = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
        boolean hover = field_146123_n && enabled;
        int bg = selected ? mix(accent, 0xFF15171B, 0.28f) : hover ? 0xFF2C3139 : 0xFF22262C;
        int border = selected ? accent : hover ? 0xFF59616B : 0xFF3A4048;
        Gui.drawRect(xPosition, yPosition, xPosition + width, yPosition + height, border);
        Gui.drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + height - 1, bg);
        // pixel bevel highlight like vanilla slots
        Gui.drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + 2, 0x22FFFFFF);
        int text = !enabled ? 0xFF6A7078 : selected ? 0xFFFFFFFF : hover ? 0xFFFFFFFF : 0xFFC8CDD3;
        String s = mc.fontRenderer.trimStringToWidth(displayString, width - 4);
        mc.fontRenderer.drawString(
            s,
            xPosition + (width - mc.fontRenderer.getStringWidth(s)) / 2,
            yPosition + (height - 8) / 2 + 1,
            text,
            selected);
    }

    static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int r = (int) (ar * t + br * (1 - t)), g = (int) (ag * t + bg * (1 - t)), bl = (int) (ab * t + bb * (1 - t));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
