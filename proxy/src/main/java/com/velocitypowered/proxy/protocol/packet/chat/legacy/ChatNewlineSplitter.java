/*
 * Copyright (C) 2026 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.protocol.packet.chat.legacy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Splits a chat message into one message per line, for Minecraft 1.7 clients. From 1.8 the chat
 * starts a new line at each newline, while 1.7 draws the newline as a character. Each line keeps
 * the style it had in the whole message.
 *
 * <p>Ported from ViaRewind's {@code ChatNewlineRewriter}, which does the same for the chat that
 * reaches 1.7 clients through ViaRewind.</p>
 */
public final class ChatNewlineSplitter {

  private static final String[] STYLE_KEYS = {
      "color",
      "bold",
      "italic",
      "underlined",
      "strikethrough",
      "obfuscated",
      "clickEvent",
      "hoverEvent",
      "insertion"
  };

  private ChatNewlineSplitter() {
  }

  /**
   * Splits a JSON chat message into one JSON message per line.
   *
   * @param json the message
   * @return the lines, or only the message itself when it has no newline
   */
  public static List<String> split(String json) {
    // A newline is always escaped in JSON
    if (!json.contains("\\n")) {
      return List.of(json);
    }

    List<JsonObject> lines = split(JsonParser.parseString(json), new JsonObject());
    if (lines.size() <= 1) {
      return List.of(json);
    }

    List<String> result = new ArrayList<>(lines.size());
    for (JsonObject line : lines) {
      result.add(line.toString());
    }
    return result;
  }

  private static List<JsonObject> split(JsonElement element, JsonObject inheritedStyle) {
    List<JsonObject> results = new ArrayList<>();
    if (element == null || element.isJsonNull()) {
      return results;
    }

    if (element.isJsonPrimitive()) {
      for (String part : element.getAsString().split("\n", -1)) {
        JsonObject piece = inheritedStyle.deepCopy();
        piece.addProperty("text", part);
        results.add(piece);
      }
      return results;
    }

    if (element.isJsonArray()) {
      // The first element of an array is the parent of the others, which inherit its style
      List<JsonObject> currentLine = new ArrayList<>();
      JsonObject siblingStyle = inheritedStyle;
      boolean foundRoot = false;

      for (JsonElement child : element.getAsJsonArray()) {
        addSplitParts(split(child, siblingStyle), currentLine, results);

        if (!foundRoot && child != null && !child.isJsonNull()) {
          siblingStyle = rootStyle(inheritedStyle, child);
          foundRoot = true;
        }
      }

      if (!currentLine.isEmpty()) {
        results.add(mergeLine(currentLine));
      }
      return results;
    }

    if (!element.isJsonObject()) {
      return results;
    }

    JsonObject object = element.getAsJsonObject();
    JsonObject currentStyle = mergeStyle(inheritedStyle, object);

    // The text comes first, then the extra components
    List<JsonElement> ordered = new ArrayList<>();
    if (object.has("text")) {
      ordered.add(object.get("text"));
    }
    if (object.has("extra") && object.get("extra").isJsonArray()) {
      for (JsonElement extra : object.getAsJsonArray("extra")) {
        ordered.add(extra);
      }
    }

    if (ordered.isEmpty()) {
      JsonObject baseComponent = copyComponentWithoutExtra(object, currentStyle);
      results.add(baseComponent != null ? baseComponent : currentStyle);
      return results;
    }

    List<JsonObject> currentLine = new ArrayList<>();
    if (!object.has("text")) {
      // Such as a translated component, which is kept whole
      JsonObject baseComponent = copyComponentWithoutExtra(object, currentStyle);
      if (baseComponent != null) {
        currentLine.add(baseComponent);
      }
    }
    for (JsonElement part : ordered) {
      if (part == null || part.isJsonNull()) {
        continue;
      }

      if (part.isJsonPrimitive()) {
        String[] split = part.getAsString().split("\n", -1);
        for (int i = 0; i < split.length; i++) {
          JsonObject piece = currentStyle.deepCopy();
          piece.addProperty("text", split[i]);
          currentLine.add(piece);

          if (i < split.length - 1) {
            results.add(mergeLine(currentLine));
            currentLine.clear();
          }
        }
      } else if (part.isJsonObject() || part.isJsonArray()) {
        addSplitParts(split(part, currentStyle), currentLine, results);
      }
    }

    if (!currentLine.isEmpty()) {
      results.add(mergeLine(currentLine));
    }
    return results;
  }

  /**
   * Adds the parts of a component to the current line, ending the line between each part.
   */
  private static void addSplitParts(List<JsonObject> splitParts, List<JsonObject> currentLine,
      List<JsonObject> results) {
    for (int i = 0; i < splitParts.size(); i++) {
      currentLine.add(splitParts.get(i));
      if (i < splitParts.size() - 1) {
        results.add(mergeLine(currentLine));
        currentLine.clear();
      }
    }
  }

  private static JsonObject rootStyle(JsonObject inheritedStyle, JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return inheritedStyle;
    }
    if (element.isJsonObject()) {
      return mergeStyle(inheritedStyle, element.getAsJsonObject());
    }
    if (element.isJsonArray()) {
      for (JsonElement child : element.getAsJsonArray()) {
        if (child != null && !child.isJsonNull()) {
          return rootStyle(inheritedStyle, child);
        }
      }
    }
    return inheritedStyle;
  }

  private static JsonObject mergeStyle(JsonObject inheritedStyle, JsonObject object) {
    JsonObject currentStyle = inheritedStyle.deepCopy();
    for (String key : STYLE_KEYS) {
      if (object.has(key)) {
        currentStyle.add(key, object.get(key).deepCopy());
      }
    }
    return currentStyle;
  }

  private static JsonObject copyComponentWithoutExtra(JsonObject object, JsonObject currentStyle) {
    JsonObject component = currentStyle.deepCopy();
    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
      String key = entry.getKey();
      if ("extra".equals(key) || isStyleKey(key)) {
        continue;
      }
      component.add(key, entry.getValue().deepCopy());
    }
    return component.size() == currentStyle.size() ? null : component;
  }

  private static boolean isStyleKey(String key) {
    for (String styleKey : STYLE_KEYS) {
      if (styleKey.equals(key)) {
        return true;
      }
    }
    return false;
  }

  private static JsonObject mergeLine(List<JsonObject> lineParts) {
    if (lineParts.isEmpty()) {
      JsonObject empty = new JsonObject();
      empty.addProperty("text", "");
      return empty;
    }
    if (lineParts.size() == 1) {
      return lineParts.get(0);
    }

    JsonObject merged = new JsonObject();
    merged.addProperty("text", "");
    JsonArray extra = new JsonArray();
    for (JsonObject linePart : lineParts) {
      extra.add(linePart);
    }
    merged.add("extra", extra);
    return merged;
  }
}
