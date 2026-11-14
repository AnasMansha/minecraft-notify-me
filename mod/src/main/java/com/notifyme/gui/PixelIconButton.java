package com.notifyme.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

/** Small button that draws a pixel-art icon instead of text. */
public class PixelIconButton extends FlatButton {

    public static final String[] CROSS = { "X...X", ".X.X.", "..X..", ".X.X.", "X...X" };
    public static final String[] BELL = { "...X...", "..XXX..", ".XXXXX.", ".XXXXX.", ".XXXXX.", "XXXXXXX", "...X..." };

    private final String[] icon;
    /** Frameless buttons only show a background while hovered (used for the small remove crosses). */
    public boolean frameless;
    public int idleColor = 0xFFB0B6BD;

    public PixelIconButton(int id, int x, int y, int w, int h, String[] icon, int accent) {
        super(id, x, y, w, h, "", accent);
        this.icon = icon;
    }

    @Override
    public void drawButton(Minecraft mc, int mx, int my) {
        if (!visible) return;
        field_146123_n = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
        boolean hover = field_146123_n && enabled;
        if (!frameless) {
            super.drawButton(mc, mx, my); // frame + background, empty label
        } else if (hover) {
            Gui.drawRect(xPosition, yPosition, xPosition + width, yPosition + height, 0x40FFFFFF & accent | 0x40000000);
        }
        int color = hover ? accent : frameless ? idleColor : accent;
        int ih = icon.length, iw = icon[0].length();
        int x0 = xPosition + (width - iw) / 2, y0 = yPosition + (height - ih) / 2;
        for (int j = 0; j < ih; j++) {
            for (int i = 0; i < iw; i++) {
                if (icon[j].charAt(i) == 'X') Gui.drawRect(x0 + i, y0 + j, x0 + i + 1, y0 + j + 1, color);
            }
        }
    }
}
