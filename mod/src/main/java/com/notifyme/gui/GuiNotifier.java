package com.notifyme.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.notifyme.NotifyMe;
import com.notifyme.data.NotifierEntry;
import com.notifyme.net.MsgOpenGui;
import com.notifyme.net.MsgSaveConfig;
import com.notifyme.util.Callsign;

/** Right-click GUI of the Redstone Notifier. */
public class GuiNotifier extends GuiScreen {

    private static final int W = 276, H = 254;
    private static final int LABEL_W = 48;
    private static final int CW = W - LABEL_W - 16; // content width
    private static final int[] COOLDOWNS = { 0, 1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 1800, 3600 };
    private static final int[] REPEATS = { 5, 10, 15, 30, 60, 120, 300, 600, 1800, 3600 };

    static final int REDSTONE = 0xFFFF3B30, GOLD = 0xFFFFC233, LABEL = 0xFF9AA1A9, DIM = 0xFF6A7078;
    private static final int[] TIER_COLORS = { 0xFF9E9E9E, 0xFF4F8DFF, 0xFF2FD36B, GOLD, REDSTONE };
    private static final String[] TIER_SHORT = { "Status", "Quiet", "Notify", "Urgent", "Alarm" };
    private static final String[] TIER_HINTS = { "Only shows ON/OFF in the app", "Silent notification",
        "Sound + vibration", "Repeats until acknowledged", "Full-screen alarm" };
    private static final String[] TRIGGER_SHORT = { "ON", "OFF", "ON+OFF", "While ON" };
    private static final String[] TRIGGER_HINTS = { "Alert when the signal turns on", "Alert when the signal turns off",
        "Alert on every change", "Alert, then repeat while it stays on" };

    private static final int B_TIER = 1, B_TRIGGER = 10, B_COOLDOWN = 20, B_REPEAT = 21, B_LINK = 30, B_SAVE = 40,
        B_TEST = 41, B_CANCEL = 42, B_STATE = 43, B_COLOR = 50, B_SUGGEST = 60, B_REMOVE = 100;

    private final int bx, by, bz;
    private final String uuid, address, linkStatus;
    private final boolean active;
    private final int strength;
    private final Map<String, String> statuses = new HashMap<>();
    private final Map<String, String> devices = new HashMap<>();
    private final Map<String, Long> lastSeen = new HashMap<>();
    private final PixelIconButton[] removeButtons = new PixelIconButton[NotifierEntry.MAX_LINKS];

    private String name, message;
    private int tier, trigger, threshold, cooldown, repeat, alarmColor;
    private boolean showState = true;
    /** Phones offered for one-click linking: {callsign, device, status}. */
    private final List<String[]> available = new ArrayList<>();
    private final List<FlatButton> suggestButtons = new ArrayList<>();
    private final SwatchButton[] swatches = new SwatchButton[NotifierEntry.ALARM_COLORS];
    private FlatButton stateButton;
    private boolean suggestionsDirty = true;
    private final List<String> links = new ArrayList<>();

    private GuiTextField nameField, messageField, callsignField;
    private final FlatButton[] tierChips = new FlatButton[5];
    private final FlatButton[] triggerChips = new FlatButton[4];
    private StepSlider cooldownSlider, repeatSlider;
    private FlatButton linkButton;
    private PixelIconButton testButton;
    private String callsignHint = "";
    private boolean meterDragging;
    private int left, top;

    public GuiNotifier(MsgOpenGui msg) {
        bx = msg.x;
        by = msg.y;
        bz = msg.z;
        NBTTagCompound t = msg.data;
        uuid = t.getString("uuid");
        name = t.getString("name");
        message = t.getString("message");
        tier = t.getInteger("tier");
        trigger = t.getInteger("trigger");
        threshold = Math.max(1, t.getInteger("threshold"));
        cooldown = t.getInteger("cooldown");
        repeat = t.getInteger("repeat");
        alarmColor = t.getInteger("alarmColor");
        showState = !t.hasKey("showState") || t.getBoolean("showState");
        NBTTagList av = t.getTagList("available", 10);
        for (int i = 0; i < av.tagCount(); i++) {
            NBTTagCompound c = av.getCompoundTagAt(i);
            available.add(new String[] { c.getString("callsign"), c.getString("device"), c.getString("status") });
            devices.put(c.getString("callsign"), c.getString("device"));
        }
        active = t.getBoolean("active");
        strength = t.getInteger("strength");
        address = t.getString("address");
        linkStatus = t.getString("linkStatus");
        NBTTagList l = t.getTagList("links", 8);
        for (int i = 0; i < l.tagCount(); i++) links.add(l.getStringTagAt(i));
        NBTTagList s = t.getTagList("statuses", 10);
        for (int i = 0; i < s.tagCount(); i++) {
            NBTTagCompound c = s.getCompoundTagAt(i);
            statuses.put(c.getString("callsign"), c.getString("status"));
            devices.put(c.getString("callsign"), c.getString("device"));
            lastSeen.put(c.getString("callsign"), c.getLong("lastSeen"));
        }
    }

    private int cx() {
        return left + 8 + LABEL_W;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        left = (width - W) / 2;
        top = Math.max(2, (height - H) / 2);
        buttonList.clear();
        int cx = cx();

        if (cooldownSlider != null) cooldown = cooldownSlider.getValue();
        if (repeatSlider != null) repeat = repeatSlider.getValue();
        String oldName = nameField == null ? name : nameField.getText();
        String oldMsg = messageField == null ? message : messageField.getText();
        String oldCs = callsignField == null ? "" : callsignField.getText();

        nameField = new GuiTextField(fontRendererObj, cx, top + 24, CW, 14);
        nameField.setMaxStringLength(40);
        nameField.setText(oldName);
        nameField.setFocused(true);
        messageField = new GuiTextField(fontRendererObj, cx, top + 42, CW - 50, 14);
        stateButton = new FlatButton(B_STATE, cx + CW - 46, top + 41, 46, 16, "is ON", REDSTONE);
        buttonList.add(stateButton);
        messageField.setMaxStringLength(100);
        messageField.setText(oldMsg);

        int chipW = (CW - 4 * 3) / 5;
        for (int i = 0; i < 5; i++) {
            tierChips[i] = new FlatButton(
                B_TIER + i,
                cx + i * (chipW + 3),
                top + 62,
                chipW,
                14,
                TIER_SHORT[i],
                TIER_COLORS[i]);
            buttonList.add(tierChips[i]);
        }
        int trigW = (CW - 3 * 3) / 4;
        for (int i = 0; i < 4; i++) {
            triggerChips[i] = new FlatButton(
                B_TRIGGER + i,
                cx + i * (trigW + 3),
                top + 92,
                trigW,
                14,
                TRIGGER_SHORT[i],
                REDSTONE);
            buttonList.add(triggerChips[i]);
        }

        int half = (CW - 4) / 2;
        cooldownSlider = new StepSlider(
            B_COOLDOWN,
            cx,
            top + 134,
            half,
            14,
            "Cooldown ",
            COOLDOWNS,
            cooldown,
            REDSTONE);
        repeatSlider = new StepSlider(
            B_REPEAT,
            cx + half + 4,
            top + 134,
            half,
            14,
            "Repeat ",
            REPEATS,
            repeat,
            REDSTONE);
        repeatSlider.disabledText = "Repeat: While ON";
        buttonList.add(cooldownSlider);
        buttonList.add(repeatSlider);

        callsignField = new GuiTextField(fontRendererObj, cx, top + 154, CW - 46, 14);
        callsignField.setMaxStringLength(40);
        callsignField.setText(oldCs);
        linkButton = new FlatButton(B_LINK, cx + CW - 42, top + 153, 42, 16, "Link", 0xFF2FD36B);
        buttonList.add(linkButton);

        for (int i = 0; i < NotifierEntry.MAX_LINKS; i++) {
            removeButtons[i] = new PixelIconButton(B_REMOVE + i, 0, 0, 9, 9, PixelIconButton.CROSS, REDSTONE);
            removeButtons[i].frameless = true;
            removeButtons[i].idleColor = 0xFF8A3A35;
            buttonList.add(removeButtons[i]);
        }
        layoutLinks();

        // Save / Cancel; the test action is the bell icon in the header
        int bw = (W - 16 - 4) / 2;
        int byy = top + H - 24;
        FlatButton save = new FlatButton(B_SAVE, left + 8, byy, bw, 18, "Save", 0xFF2FD36B);
        save.selected = true;
        buttonList.add(save);
        buttonList.add(new FlatButton(B_CANCEL, left + 12 + bw, byy, bw, 18, "Cancel", 0xFF9E9E9E));
        testButton = new PixelIconButton(B_TEST, pillX() - 18, top + 5, 14, 14, PixelIconButton.BELL, GOLD);
        buttonList.add(testButton);

        // alarm colour: right side of the tier hint line, only shown for the Alarm tier
        for (int i = 0; i < swatches.length; i++) {
            swatches[i] = new SwatchButton(
                B_COLOR + i,
                cx + CW - (swatches.length - i) * 12 + 2,
                top + 77,
                10,
                NotifierEntry.ALARM_COLOR_RGB[i]);
            buttonList.add(swatches[i]);
        }
        layoutSuggestions();
        refreshButtons();
    }

    private String pillText() {
        return (active ? "ON" : "OFF") + "  \u00B7  strength " + strength;
    }

    private int pillX() {
        return left + W - 8 - fontRendererObj.getStringWidth(pillText()) - 10;
    }

    private static final int LINK_TOP = 185, LINK_ROW = 11;

    /** Puts each remove cross right after its callsign. */
    private void layoutLinks() {
        int colW = (W - 16) / 2;
        for (int i = 0; i < NotifierEntry.MAX_LINKS; i++) {
            PixelIconButton b = removeButtons[i];
            if (b == null) continue;
            b.visible = i < links.size();
            if (!b.visible) continue;
            int lx = left + 8 + (i % 2) * colW, ly = top + LINK_TOP + (i / 2) * LINK_ROW;
            String label = fontRendererObj.trimStringToWidth(links.get(i), colW - 24);
            b.xPosition = lx + 9 + fontRendererObj.getStringWidth(label) + 3;
            b.yPosition = ly - 1;
        }
    }

    /** One-click "+ Callsign" chips under the callsign field, for phones that are connected / known. */
    private void layoutSuggestions() {
        buttonList.removeAll(suggestButtons);
        suggestButtons.clear();
        int x = cx(), end = cx() + CW;
        for (int i = 0; i < available.size(); i++) {
            String[] p = available.get(i);
            if (links.contains(p[0])) continue;
            String label = "+ " + p[0];
            int w = fontRendererObj.getStringWidth(label) + 8;
            if (x + w > end) break;
            int accent = "online".equals(p[2]) ? 0xFF2FD36B : "pending".equals(p[2]) ? GOLD : 0xFF9E9E9E;
            FlatButton b = new FlatButton(B_SUGGEST + i, x, top + 170, w, 12, label, accent);
            suggestButtons.add(b);
            buttonList.add(b);
            x += w + 3;
        }
    }

    private boolean showSuggestions() {
        return !suggestButtons.isEmpty() && callsignField.getText()
            .trim()
            .isEmpty();
    }

    private void refreshButtons() {
        for (int i = 0; i < 5; i++) tierChips[i].selected = i == tier;
        for (int i = 0; i < 4; i++) triggerChips[i].selected = i == trigger;
        repeatSlider.enabled = trigger == NotifierEntry.TRIGGER_WHILE_ON;
        layoutLinks();
        linkButton.enabled = links.size() < NotifierEntry.MAX_LINKS;
        updateCallsignHint();
        stateButton.selected = showState;
        stateButton.displayString = showState ? "is ON" : "name";
        for (int i = 0; i < swatches.length; i++) {
            swatches[i].visible = tier == 4;
            swatches[i].selected = i == alarmColor;
        }
        suggestionsDirty = true; // re-laid out next frame: never change buttonList inside a click
    }

    /** First LAN address (prefers 192.168.x) + port, e.g. "192.168.1.5:25570". */
    private String shortAddress() {
        String[] parts = address.split(" {2}port ");
        String port = parts.length > 1 ? parts[1].trim() : "25570";
        String best = "";
        for (String a : parts[0].split(",")) {
            a = a.trim();
            if (best.isEmpty() || a.startsWith("192.168.")) best = a;
            if (a.startsWith("192.168.")) break;
        }
        return best + ":" + port;
    }

    private void updateCallsignHint() {
        String text = callsignField.getText()
            .trim();
        if (text.isEmpty()) {
            callsignHint = EnumChatFormatting.GRAY + "App > Scan LAN or " + shortAddress();
            return;
        }
        String cs = Callsign.normalize(text);
        if (cs == null) callsignHint = EnumChatFormatting.RED + "Unknown callsign or wrong check digit";
        else if (links.contains(cs)) callsignHint = EnumChatFormatting.YELLOW + cs + " is already linked";
        else callsignHint = EnumChatFormatting.GREEN + cs + EnumChatFormatting.GRAY + " - press Link";
    }

    private void linkCallsign() {
        String cs = Callsign.normalize(callsignField.getText());
        if (cs == null || links.contains(cs) || links.size() >= NotifierEntry.MAX_LINKS) return;
        links.add(cs);
        statuses.put(cs, "unsaved");
        callsignField.setText("");
        refreshButtons();
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id >= B_TIER && b.id < B_TIER + 5) tier = b.id - B_TIER;
        else if (b.id >= B_TRIGGER && b.id < B_TRIGGER + 4) trigger = b.id - B_TRIGGER;
        else if (b.id == B_LINK) linkCallsign();
        else if (b.id == B_STATE) showState = !showState;
        else if (b.id >= B_COLOR && b.id < B_COLOR + swatches.length) alarmColor = b.id - B_COLOR;
        else if (b.id >= B_SUGGEST && b.id < B_SUGGEST + available.size()) {
            String[] p = available.get(b.id - B_SUGGEST);
            if (!links.contains(p[0]) && links.size() < NotifierEntry.MAX_LINKS) {
                links.add(p[0]);
                statuses.put(p[0], "unsaved");
            }
        } else if (b.id == B_SAVE || b.id == B_TEST) {
            // the bell saves first so the test uses what is on screen
            send(b.id == B_TEST ? MsgSaveConfig.ACTION_SAVE_AND_TEST : MsgSaveConfig.ACTION_SAVE);
            mc.displayGuiScreen(null);
            return;
        } else if (b.id == B_CANCEL) {
            mc.displayGuiScreen(null);
            return;
        } else if (b.id >= B_REMOVE && b.id - B_REMOVE < links.size()) links.remove(b.id - B_REMOVE);
        refreshButtons();
    }

    private void send(int action) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("name", nameField.getText());
        t.setString("message", messageField.getText());
        t.setInteger("tier", tier);
        t.setInteger("trigger", trigger);
        t.setInteger("threshold", threshold);
        t.setInteger("cooldown", cooldownSlider.getValue());
        t.setInteger("repeat", repeatSlider.getValue());
        t.setInteger("alarmColor", alarmColor);
        t.setBoolean("showState", showState);
        NBTTagList l = new NBTTagList();
        for (String s : links) l.appendTag(new NBTTagString(s));
        t.setTag("links", l);
        NotifyMe.NET.sendToServer(new MsgSaveConfig(bx, by, bz, action, t));
    }

    // ------------------------------------------------------------------ signal meter (threshold)

    private static final int CELL = 12, METER_CELLS = 15;

    private int meterX() {
        return cx();
    }

    private int meterY() {
        return top + 114;
    }

    /** 1..15 if the mouse is over a meter cell, else 0. */
    private int meterCellAt(int mx, int my) {
        int x0 = meterX(), y0 = meterY();
        if (my < y0 - 1 || my >= y0 + 13 || mx < x0 || mx >= x0 + CELL * METER_CELLS) return 0;
        return (mx - x0) / CELL + 1;
    }

    private void drawMeter(int mx, int my) {
        int x0 = meterX(), y0 = meterY();
        int hoverCell = meterCellAt(mx, my);
        for (int i = 1; i <= METER_CELLS; i++) {
            int x = x0 + (i - 1) * CELL;
            boolean lit = i <= strength;
            boolean onZone = i >= threshold;
            int fill;
            if (lit) fill = FlatButton.mix(0xFFFF2A1A, 0xFF3A0A08, 0.35f + 0.65f * i / 15f);
            else fill = onZone ? 0xFF3A2020 : 0xFF24262B;
            drawRect(x, y0, x + CELL - 1, y0 + 12, 0xFF000000);
            drawRect(x + 1, y0 + 1, x + CELL - 2, y0 + 11, fill);
            if (lit) drawRect(x + 2, y0 + 2, x + CELL - 3, y0 + 3, 0x66FFFFFF);
            if (onZone) drawRect(x + 1, y0 + 10, x + CELL - 2, y0 + 11, GOLD);
            if (i == threshold) {
                drawRect(x - 1, y0 - 1, x + CELL, y0, GOLD);
                drawRect(x - 1, y0 + 12, x + CELL, y0 + 13, GOLD);
                drawRect(x - 1, y0 - 1, x, y0 + 13, GOLD);
                drawRect(x + CELL - 1, y0 - 1, x + CELL, y0 + 13, GOLD);
            }
            if (i == hoverCell) drawRect(x + 1, y0 + 1, x + CELL - 2, y0 + 11, 0x40FFFFFF);
        }
        String t = "≥" + threshold;
        fontRendererObj.drawStringWithShadow(t, x0 + CELL * METER_CELLS + 4, y0 + 2, GOLD);
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_TAB) {
            GuiTextField[] f = { nameField, messageField, callsignField };
            int cur = 0;
            for (int i = 0; i < f.length; i++) if (f[i].isFocused()) cur = i;
            for (GuiTextField x : f) x.setFocused(false);
            f[(cur + 1) % f.length].setFocused(true);
            return;
        }
        if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) && callsignField.isFocused()) {
            linkCallsign();
            return;
        }
        if (nameField.textboxKeyTyped(c, key) || messageField.textboxKeyTyped(c, key)) return;
        if (callsignField.textboxKeyTyped(c, key)) {
            refreshButtons();
            return;
        }
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        super.mouseClicked(mx, my, button);
        nameField.mouseClicked(mx, my, button);
        messageField.mouseClicked(mx, my, button);
        callsignField.mouseClicked(mx, my, button);
        int cell = meterCellAt(mx, my);
        if (button == 0 && cell > 0) {
            threshold = cell;
            meterDragging = true;
        }
    }

    @Override
    public void updateScreen() {
        nameField.updateCursorCounter();
        messageField.updateCursorCounter();
        callsignField.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ------------------------------------------------------------------ drawing

    private int hoveredSwatch() {
        for (int i = 0; i < swatches.length; i++) if (swatches[i].visible && swatches[i].func_146115_a()) return i;
        return -1;
    }

    private int hoveredSuggestion() {
        for (int i = 0; i < suggestButtons.size(); i++) if (suggestButtons.get(i).visible && suggestButtons.get(i)
            .func_146115_a()) return i;
        return -1;
    }

    /** Index of the linked phone row under the mouse, or -1. */
    private int hoveredPhone(int mx, int my) {
        int colW = (W - 16) / 2;
        for (int i = 0; i < links.size(); i++) {
            int lx = left + 8 + (i % 2) * colW, ly = top + LINK_TOP + (i / 2) * LINK_ROW;
            int textEnd = removeButtons[i] != null ? removeButtons[i].xPosition - 1 : lx + colW;
            if (mx >= lx && mx < textEnd && my >= ly - 1 && my < ly + 9) return i;
        }
        return -1;
    }

    private List<String> phoneTooltip(String cs) {
        List<String> tip = new ArrayList<>();
        tip.add(cs);
        String device = devices.get(cs);
        if (device != null && !device.isEmpty()) tip.add(EnumChatFormatting.AQUA + device);
        String s = statuses.containsKey(cs) ? statuses.get(cs) : "unsaved";
        switch (s) {
            case "online":
                tip.add(EnumChatFormatting.GREEN + "Online now");
                break;
            case "pending":
                tip.add(EnumChatFormatting.GOLD + "Waiting for approval - click Approve in chat");
                break;
            case "offline":
                tip.add(EnumChatFormatting.RED + "Offline");
                break;
            case "never":
                tip.add(EnumChatFormatting.GRAY + "Has not connected yet");
                break;
            default:
                tip.add(EnumChatFormatting.GRAY + "Not saved yet");
        }
        Long seen = lastSeen.get(cs);
        if (seen != null && seen > 0 && !"online".equals(s))
            tip.add(EnumChatFormatting.GRAY + "Last seen " + ago(seen));
        return tip;
    }

    private static String ago(long ts) {
        long m = Math.max(0, (System.currentTimeMillis() - ts) / 60_000L);
        if (m < 1) return "just now";
        if (m < 60) return m + " min ago";
        if (m < 48 * 60) return (m / 60) + " h ago";
        return (m / 1440) + " days ago";
    }

    private void label(String s, int y) {
        fontRendererObj.drawString(s, left + 8, y, LABEL);
    }

    @Override
    public void drawScreen(int mx, int my, float partial) {
        if (suggestionsDirty) {
            layoutSuggestions();
            suggestionsDirty = false;
        }
        boolean sug = showSuggestions();
        for (FlatButton b : suggestButtons) b.visible = sug;
        if (meterDragging) {
            if (Mouse.isButtonDown(0)) {
                int x0 = meterX();
                threshold = Math.max(1, Math.min(15, (mx - x0) / CELL + 1));
            } else meterDragging = false;
        }
        drawDefaultBackground();
        // panel: black outline, deepslate fill, bevel, redstone accent
        drawRect(left - 1, top - 1, left + W + 1, top + H + 1, 0xFF000000);
        drawRect(left, top, left + W, top + H, 0xF21C1E23);
        drawRect(left, top, left + W, top + 1, 0x30FFFFFF);
        drawRect(left, top, left + 1, top + H, 0x30FFFFFF);
        drawRect(left, top + H - 1, left + W, top + H, 0x60000000);
        drawRect(left, top, left + W, top + 2, REDSTONE);

        fontRendererObj.drawStringWithShadow("Redstone Notifier", left + 8, top + 8, 0xFFFFFF);
        String st = pillText();
        int px = pillX();
        drawRect(px, top + 6, left + W - 6, top + 18, active ? 0xFF4A1210 : 0xFF2A2D33);
        drawRect(px + 3, top + 10, px + 7, top + 14, active ? REDSTONE : DIM);
        fontRendererObj.drawString(st, px + 10, top + 8, active ? 0xFFFF8A80 : 0xFFB0B6BD);

        label("Name", top + 27);
        label("Message", top + 45);
        nameField.drawTextBox();
        messageField.drawTextBox();

        label("Tier", top + 65);
        String hint = fontRendererObj.trimStringToWidth(TIER_HINTS[tier], tier == 4 ? CW - 76 : CW);
        fontRendererObj.drawString(hint, cx(), top + 80, TIER_COLORS[tier]);

        label("When", top + 95);

        label("Strength", top + 117);
        drawMeter(mx, my);

        label("Timing", top + 137);

        label("Phone", top + 157);
        callsignField.drawTextBox();
        if (!showSuggestions())
            fontRendererObj.drawString(fontRendererObj.trimStringToWidth(callsignHint, CW), cx(), top + 172, 0xFFFFFF);

        int colW = (W - 16) / 2;
        for (int i = 0; i < links.size(); i++) {
            int col = i % 2, row = i / 2;
            String cs = links.get(i);
            String s = statuses.containsKey(cs) ? statuses.get(cs) : "unsaved";
            int color;
            switch (s) {
                case "online":
                    color = 0xFF2FD36B;
                    break;
                case "pending":
                    color = GOLD;
                    break;
                case "offline":
                    color = REDSTONE;
                    break;
                default:
                    color = 0xFF888888;
            }
            int lx = left + 8 + col * colW, ly = top + 185 + row * 11;
            drawRect(lx, ly + 1, lx + 6, ly + 7, 0xFF000000);
            drawRect(lx + 1, ly + 2, lx + 5, ly + 6, color);
            fontRendererObj.drawString(fontRendererObj.trimStringToWidth(cs, colW - 24), lx + 9, ly, 0xDDDDDD);
        }
        if (links.isEmpty()) fontRendererObj.drawString("No phones linked yet.", left + 8, top + 185, DIM);
        layoutLinks();

        super.drawScreen(mx, my, partial);

        // tooltips (drawn last so they sit on top)
        int cell = meterCellAt(mx, my);
        FlatButton hoveredTrigger = null;
        for (FlatButton b : triggerChips) if (b.func_146115_a()) hoveredTrigger = b;
        if (hoveredTrigger != null) {
            List<String> tip = new ArrayList<>();
            tip.add(TRIGGER_HINTS[hoveredTrigger.id - B_TRIGGER]);
            drawHoveringText(tip, mx, my, fontRendererObj);
        } else if (cell > 0) {
            List<String> tip = new ArrayList<>();
            tip.add("Click or drag: ON at strength ≥ " + cell);
            tip.add(
                EnumChatFormatting.GRAY + "Now: strength " + strength + (strength >= threshold ? " (ON)" : " (OFF)"));
            tip.add(EnumChatFormatting.GRAY + "Lit cells = current strength, gold = counts as ON");
            drawHoveringText(tip, mx, my, fontRendererObj);
        } else if (mx >= cx() && mx < cx() + CW && my >= top + 170 && my < top + 181) {
            List<String> tip = new ArrayList<>();
            tip.add("Link server: " + linkStatus);
            tip.add("Addresses: " + address);
            tip.add(EnumChatFormatting.GRAY + "Block id: " + uuid);
            drawHoveringText(tip, mx, my, fontRendererObj);
        } else if (stateButton.func_146115_a()) {
            List<String> tip = new ArrayList<>();
            tip.add(showState ? "Phone shows \"" + nameField.getText() + " is ON\"" : "Phone shows just the name");
            tip.add(EnumChatFormatting.GRAY + "Click to toggle \"is ON / is OFF\" after the name");
            drawHoveringText(tip, mx, my, fontRendererObj);
        } else if (hoveredSwatch() >= 0) {
            List<String> tip = new ArrayList<>();
            tip.add("Alarm colour: " + NotifierEntry.ALARM_COLOR_NAMES[hoveredSwatch()]);
            tip.add(EnumChatFormatting.GRAY + "Background of the full-screen alarm on the phone");
            drawHoveringText(tip, mx, my, fontRendererObj);
        } else if (hoveredSuggestion() >= 0) {
            String[] p = available.get(suggestButtons.get(hoveredSuggestion()).id - B_SUGGEST);
            List<String> tip = new ArrayList<>();
            tip.add("Link " + p[0]);
            if (!p[1].isEmpty()) tip.add(EnumChatFormatting.AQUA + p[1]);
            tip.add(
                "online".equals(p[2]) ? EnumChatFormatting.GREEN + "Connected now"
                    : "pending".equals(p[2]) ? EnumChatFormatting.GOLD + "Connected, will be approved when you save"
                        : EnumChatFormatting.GRAY + "Known phone, offline");
            drawHoveringText(tip, mx, my, fontRendererObj);
        } else if (testButton.func_146115_a()) {
            List<String> tip = new ArrayList<>();
            tip.add("Test");
            tip.add(EnumChatFormatting.GRAY + "Saves, then sends a test alert to linked phones");
            drawHoveringText(tip, mx, my, fontRendererObj);
        } else {
            int phone = hoveredPhone(mx, my);
            if (phone >= 0) drawHoveringText(phoneTooltip(links.get(phone)), mx, my, fontRendererObj);
        }
    }
}
