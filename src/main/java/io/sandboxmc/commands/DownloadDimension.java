package io.sandboxmc.commands;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.sandboxmc.Web;
import io.sandboxmc.commands.autoComplete.WebAutoComplete;
import io.sandboxmc.dimension.DimensionManager;
import io.sandboxmc.mixin.MinecraftServerAccessor;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.level.storage.LevelStorage.Session;

public class DownloadDimension {
  public static LiteralArgumentBuilder<ServerCommandSource> register() {
    return CommandManager.literal("download")
      .then(
        CommandManager.argument("creator", StringArgumentType.word())
        .suggests(new WebAutoComplete("creators"))
        .then(
          CommandManager.argument("dimension", StringArgumentType.word())
          .suggests(new WebAutoComplete("dimensions", "creators", "creator"))
          .then(
            CommandManager.argument("customFileName", StringArgumentType.word())
            .executes(context -> performDownloadCmd(context))
          )
          .executes(context -> performDownloadCmd(context))
        )
        .executes(context -> {
          sendFeedback(context.getSource(), Text.literal("No dimension given."));
          return 0;
        })
      )
      .executes(context -> {
        sendFeedback(context.getSource(), Text.literal("No creator or dimension given."));
        return 0;
      });
  }

  private static int performDownloadCmd(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
    ServerCommandSource source = context.getSource();
    String creatorName = StringArgumentType.getString(context, "creator");
    String identifier = StringArgumentType.getString(context, "dimension");
    String customFileName;
    try {
      customFileName = StringArgumentType.getString(context, "customFileName");
    } catch (IllegalArgumentException e) {
      // this is a nullable argument
      customFileName = null;
    }

    // Make sure the URL is formed properly and can be accessed as an InputStream.
    String dimensionPath = "/dimensions/" + creatorName + "/" + identifier;

    Web web = new Web(context.getSource(), dimensionPath + "/download");

    InputStream inputStream;
    try {
      inputStream = web.getInputStream();
    } catch (IOException | InterruptedException e) {
      sendFeedback(source, Text.literal("No dimension found at\n" + Web.WEB_DOMAIN + dimensionPath + "\nDid you misstype it?"));
      return 0;
    }

    // Pull the URL into a channel
    ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream);

    FileOutputStream fileOutputStream;

    String filePath;
    Session session = ((MinecraftServerAccessor)source.getServer()).getSession();
    if (customFileName == null) {
      filePath = defaultFilePath(session, creatorName, identifier);
    } else {
      filePath = customFilePath(session, customFileName);
    }

    try {
      fileOutputStream = new FileOutputStream(filePath);
    } catch (FileNotFoundException e) {
      // This can't actually happen, customFilePath and defaultFilePath both ensure everything.
      // Existing files are replaced.
      return 0;
    }
  
    FileChannel fileChannel = fileOutputStream.getChannel();

    try {
      fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
      fileOutputStream.close();
      inputStream.close();
    } catch (IOException e) {
      System.out.println("io exception reading file from channel");
      return 0;
    }

    MutableText feedbackText = Text.literal("Dimension downloaded!\n\n");
    MutableText creationText = Text.literal("[CLICK HERE TO CREATE IT]");
    // TODO: Need to run some sort of unpack method here.
    ClickEvent unpackEvent = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dimension download awef awef");
    creationText.setStyle(Style.EMPTY.withClickEvent(unpackEvent));
    creationText.formatted(Formatting.UNDERLINE).formatted(Formatting.BLUE);
    feedbackText.append(creationText);
    sendFeedback(source, feedbackText);

    return 1;
  }

  private static String defaultFilePath(Session session, String creatorName, String identifier) {
    Path storageFolder = DimensionManager.getStorageFolder(session);
    String creatorFolderName = Paths.get(storageFolder.toString(), creatorName).toString();
    File creatorDirFile = new File(creatorFolderName);
    if (!creatorDirFile.exists()) {
      creatorDirFile.mkdir();
    }

    return Paths.get(storageFolder.toString(), creatorName, identifier + ".zip").toString();
  }

  private static String customFilePath(Session session, String customFileName) {
    Path storageFolder = DimensionManager.getStorageFolder(session);

    if (!customFileName.endsWith(".zip")) {
      customFileName += ".zip";
    }

    String filePath = Paths.get(storageFolder.toString(), customFileName).toString();

    return filePath;
  }

  // TODO: Pull this into a more globally available helper...
  private static void sendFeedback(ServerCommandSource source, Text feedbackText) {
    source.sendFeedback(() -> {
      return feedbackText;
    }, false);
  }
}
