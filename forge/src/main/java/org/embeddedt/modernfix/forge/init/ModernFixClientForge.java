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
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.forge.config.NightConfigFixer;
import org.embeddedt.modernfix.screen.ModernFixConfigScreen;

public final class ModernFixClientForge {
    private static final ModernFixClient COMMON = new ModernFixClient();
    private static final String[] BRANDING = new String[] {"", COMMON.brandingString};
    private KeyMapping configKey;

    public ModernFixClientForge() {
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
        if(ModernFixMixinPlugin.instance.isOptionEnabled("perf.dynamic_resources.ConnectednessCheck")
                && ModList.get().isLoaded("connectedness")) {
            event.enqueueWork(() ->
                    ModLoadingContext.get().getContainer().getModInfo().getOwningFile()
                            .addWarning("modernfix.connectedness_dynresoruces")
            );
        }
    }

    @SubscribeEvent
    public void onConfigKey(TickEvent.ClientTickEvent event) {
        if(event.phase == TickEvent.Phase.START && configKey != null && configKey.consumeClick()) {
            Minecraft.getInstance().setScreen(new ModernFixConfigScreen(Minecraft.getInstance().screen));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onClientChat(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("mfrc")
                        .executes(ctx -> { NightConfigFixer.runReloads(); return 1; })
        );
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderOverlay(CustomizeGuiOverlayEvent.DebugText event) {
        if(COMMON.brandingString != null && Minecraft.getInstance().options.renderDebug().get()) {
            var right = event.getRight();
            int blanks = 0, idx = 0;
            while(idx < right.size() && blanks < 3) {
                if(right.get(idx++).isEmpty()) blanks++;
            }
            if(blanks == 3) {
                right.add(idx, BRANDING[0]);
                right.add(idx + 1, BRANDING[1]);
            }
        }
    }

    @SubscribeEvent
    public void onDisconnect(LevelEvent.Unload event) {
        if(event.getLevel().isClientSide()) {
            DebugScreenOverlay overlay = ObfuscationReflectionHelper.getPrivateValue(
                    ForgeGui.class,
                    (ForgeGui)Minecraft.getInstance().gui,
                    "debugOverlay"
            );
            if(overlay != null) Minecraft.getInstance().tell(overlay::clearChunkCache);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartedEvent event) {
        COMMON.onServerStarted(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderTickEnd(TickEvent.RenderTickEvent event) {
        if(event.phase == TickEvent.Phase.END) COMMON.onRenderTickEnd();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRecipes(RecipesUpdatedEvent e) {
        COMMON.onRecipesUpdated();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTags(TagsUpdatedEvent e) {
        COMMON.onTagsUpdated();
    }
}
