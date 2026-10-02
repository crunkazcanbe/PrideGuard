package com.dogpound.prideguard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

/** client side: the menu key (unbound by default — /pg gui opens it too) and the latest data from the server */
public final class ClientProxy {
    public static volatile Net.Snapshot snapshot;
    static final KeyBinding OPEN = new KeyBinding("Open PrideGuard (admins)", Keyboard.KEY_NONE, "PrideGuard");

    static void register() {
        ClientRegistry.registerKeyBinding(OPEN);
        MinecraftForge.EVENT_BUS.register(new ClientProxy());
    }

    @SubscribeEvent
    public void key(InputEvent.KeyInputEvent e) {
        if (OPEN.isPressed() && Minecraft.getMinecraft().currentScreen == null) openMenu();
    }

    static void openMenu() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiGuard());
    }
}
