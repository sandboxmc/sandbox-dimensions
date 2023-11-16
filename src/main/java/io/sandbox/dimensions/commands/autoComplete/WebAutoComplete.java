package io.sandbox.dimensions.commands.autoComplete;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import com.google.gson.stream.JsonReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.server.command.ServerCommandSource;

public class WebAutoComplete implements SuggestionProvider<ServerCommandSource> {

  public static ArrayList<String> VALID_TYPES = new ArrayList<String>(Arrays.asList("creators", "dimensions"));
  private String urlPart = null;

  public WebAutoComplete(String autocompleteType) {
    if (VALID_TYPES.indexOf(autocompleteType) != -1) {
      urlPart = autocompleteType;
    }
  }

  public WebAutoComplete(String autocompleteType, String prefixType, String prefixValue) {
    this(autocompleteType);

    if (VALID_TYPES.indexOf(prefixType) != -1 && !prefixValue.matches("[^\\w-]")) {
      urlPart = prefixType + "/" + prefixValue + "/" + urlPart;
    }
  }

  @Override
  public CompletableFuture<Suggestions> getSuggestions(
    CommandContext<ServerCommandSource> context,
    SuggestionsBuilder builder
  ) throws CommandSyntaxException {
    if (urlPart.isBlank()) {
      // Somehow we got a value we weren't expecting in the constructor!
      return builder.buildFuture();
    }

    String remaining = builder.getRemaining();

    if (remaining.length() == 0) {
      return builder.buildFuture();
    }

    URL url;
    InputStream inputStream;
    ArrayList<HashMap<String, String>> valuesToSuggest = new ArrayList<>();
    try {
      url = URI.create("https://www.sandboxmc.io/autocomplete/" + urlPart + "?q=" + remaining).toURL();
      inputStream = url.openStream();
      readJSON(inputStream, valuesToSuggest);
      inputStream.close();
    } catch (IOException e) {
      System.out.println("GOT AN ERROR\n" + e.getMessage());
    }

    valuesToSuggest.forEach((valueToSuggest) -> {
      builder.suggest(valueToSuggest.get("value"), new LiteralMessage(valueToSuggest.get("label")));
    });

    return builder.buildFuture();
  }

  // @see https://stackoverflow.com/questions/4308554/simplest-way-to-read-json-from-a-url-in-java
  private void readJSON(InputStream inputStream, ArrayList<HashMap<String, String>> valuesToSuggest) throws IOException {
    InputStreamReader reader = new InputStreamReader(inputStream, Charset.forName("UTF-8"));
    JsonReader jsonReader = new JsonReader(reader);

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String key = jsonReader.nextName();
      switch (key) {
        case "total":
          jsonReader.skipValue(); // TODO: determine if the total count can be used
          break;
        case "message":
          jsonReader.skipValue(); // TODO: determine if error messages can be used
          break;
        case "results":
          jsonReader.beginArray();
          while (jsonReader.hasNext()) {
            jsonReader.beginArray();

            HashMap<String, String> suggestResult = new HashMap<>();
            suggestResult.put("value", jsonReader.nextString());
            suggestResult.put("label", jsonReader.nextString());
            valuesToSuggest.add(suggestResult);

            jsonReader.endArray();
          }
          jsonReader.endArray();
          break;
        default:
          // Just ignore anything else
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();
    jsonReader.close();
  }
}
