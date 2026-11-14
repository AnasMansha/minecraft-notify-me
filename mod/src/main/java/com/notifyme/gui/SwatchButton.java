package com.notifyme.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

/** Small colour square; white frame when selected. */
public class SwatchButton extends GuiButton {

    public final int color;
    public boolean selected;

    public SwatchButton(int id, int x, int y, int size, int color) {
        super(id, x, y, size, size, "");
        this.color = color;
    }

    @Override
    public void drawButton(Minecraft mc, int mx, int my) {
        if (!visible) return;
        field_146123_n = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
        int frame = selected ? 0xFFFFFFFF : field_146123_n ? 0xFF9AA1A9 : 0xFF000000;
        Gui.drawRect(xPosition, yPosition, xPosition + width, yPosition + height, frame);
        Gui.drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + height - 1, color);
        Gui.drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + 2, 0x40FFFFFF);
    }
}
