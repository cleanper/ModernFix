package org.embeddedt.modernfix.forge.init;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.forge.config.NightConfigFixer;
import org.embeddedt.modernfix.screen.ModernFixConfigScreen;

import java.util.ArrayList;
import java.util.List;

public class ModernFixClientForge {
    private static ModernFixClient commonMod;
    private static final List<String> brandingList = new ArrayList<>();
    private KeyMapping configKey;

    public ModernFixClientForge() {
        commonMod = new ModernFixClient();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::keyBindRegister);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, screen) -> new ModernFixConfigScreen(screen))
        );
    }

    private void keyBindRegister(RegisterKeyMappingsEvent event) {
        configKey = new KeyMapping("key.modernfix.config", KeyConflictContext.UNIVERSAL, InputConstants.UNKNOWN, "key.modernfix");
        event.register(configKey);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        if (ModernFixMixinPlugin.instance.isOptionEnabled("perf.dynamic_resources.ConnectednessCheck") && ModList.get().isLoaded("connectedness")) {
            event.enqueueWork(() -> ModLoader.get().addWarning(new ModLoadingWarning(ModLoadingContext.get().getActiveContainer().getModInfo(), ModLoadingStage.SIDED_SETUP, "modernfix.connectedness_dynresoruces")));
        }
    }

    @SubscribeEvent
    public void onConfigKey(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START && configKey != null && configKey.consumeClick()) {
            Minecraft.getInstance().setScreen(new ModernFixConfigScreen(Minecraft.getInstance().screen));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onClientChat(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(LiteralArgumentBuilder.<CommandSourceStack>literal("mfrc")
                .executes(context -> {
                    NightConfigFixer.runReloads();
                    return 1;
                }));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderOverlay(CustomizeGuiOverlayEvent.DebugText event) {
        if (commonMod.brandingString != null && Minecraft.getInstance().options.renderDebug()) {
            if (brandingList.isEmpty()) {
                brandingList.add("");
                brandingList.add(commonMod.brandingString);
            }
            List<String> right = event.getRight();
            int blanks = 0, idx = 0;
            while (idx < right.size() && blanks < 3) {
                if (right.get(idx).isEmpty()) blanks++;
                idx++;
            }
            right.addAll(idx, brandingList);
        }
    }

    @SubscribeEvent
    public void onDisconnect(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            DebugScreenOverlay overlay = ObfuscationReflectionHelper.getPrivateValue(ForgeGui.class, (ForgeGui) Minecraft.getInstance().gui, "debugOverlay");
            if (overlay != null) Minecraft.getInstance().tell(overlay::clearChunkCache);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartedEvent event) {
        commonMod.onServerStarted(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderTickEnd(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) commonMod.onRenderTickEnd();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRecipes(RecipesUpdatedEvent e) {
        commonMod.onRecipesUpdated();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTags(TagsUpdatedEvent e) {
        commonMod.onTagsUpdated();
    }
}
