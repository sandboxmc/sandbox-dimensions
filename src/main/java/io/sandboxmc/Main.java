package io.sandboxmc;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.util.Identifier;
import io.sandboxmc.configs.PlayerRespawnConfig;
import io.sandboxmc.dimension.DimensionManager;
// import io.sandbox.lib.SandboxLogger;
import io.sandboxmc.commands.CommandInit;

public class Main implements ModInitializer {
  public static final String modId = "sandboxmc";
  public static final String WEB_DOMAIN = "https://www.sandboxmc.dev";
  // This logger is used to write text to the console and the log file.
  // It is considered best practice to use your mod id as the logger's name.
  // That way, it's clear which mod wrote info, warnings, and errors.
  // public static final SandboxLogger LOGGER = new SandboxLogger("SandboxDimensions");

  @Override
  public void onInitialize() {
    // This code runs as soon as Minecraft is in a mod-load-ready state.
    // However, some things (like resources) may still be uninitialized.
    // Proceed with mild caution.

    ServerLifecycleEvents.SERVER_STARTED.register(new ServerStartedListener());

    // Initialize Commands
    CommandInit.init();
    DimensionManager.init();
    PlayerRespawnConfig.initRespawnListener();
  }

  public static Identifier id(String name) {
    return new Identifier(Main.modId, name);
  }
}
