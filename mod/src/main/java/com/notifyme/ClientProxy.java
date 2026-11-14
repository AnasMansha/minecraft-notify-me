package com.notifyme;

import net.minecraft.client.Minecraft;

import com.notifyme.gui.GuiNotifier;
import com.notifyme.net.MsgOpenGui;

public class ClientProxy extends CommonProxy {

    @Override
    public void openNotifierGui(MsgOpenGui msg) {
        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiNotifier(msg));
    }
}
