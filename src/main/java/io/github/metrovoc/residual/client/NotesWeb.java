package io.github.metrovoc.residual.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import guideme.siteexport.ExportPostProcessor;

/**
 * The website's side of the field notes' tags of their own: the export rewrites steps, shots and live widgets to plain elements
 * with what they need in {@code data-} attributes, the generator passes those through as HTML, and {@code src/website/live.js}
 * brings them to life.
 */
final class NotesWeb implements ExportPostProcessor {
    @Override
    public void postProcess(JsonObject root) {
        for (JsonElement page : root.getAsJsonObject("pages").asMap().values()) {
            rewrite(page.getAsJsonObject().getAsJsonObject("astRoot"));
        }
    }

    private static void rewrite(JsonObject node) {
        if (!(node.get("children") instanceof JsonArray children)) {
            return;
        }
        for (JsonElement element : children) {
            JsonObject child = element.getAsJsonObject();
            if (child.get("name") instanceof JsonPrimitive name) {
                switch (name.getAsString()) {
                    case "residual:Steps" -> become(child, "div", "data-steps", "");
                    case "residual:Step" -> become(child, "section", "data-step", "");
                    case "residual:Part" -> become(child, "li", "data-part", attribute(child, "name"));
                    case "residual:Shot" -> shot(child);
                    case "residual:Softmax", "residual:Attention", "residual:Curve" -> widget(child, name.getAsString());
                    default -> {
                    }
                }
            }
            rewrite(child);
        }
    }

    /** The picture is the script's to place, as it needs the way to the site's root; the parts become a numbered list under it. */
    private static void shot(JsonObject shot) {
        JsonObject list = new JsonObject();
        list.addProperty("type", "mdxJsxFlowElement");
        list.addProperty("name", "ol");
        list.add("attributes", new JsonArray());
        list.add("children", shot.getAsJsonArray("children"));
        JsonArray children = new JsonArray();
        children.add(list);
        String name = attribute(shot, "name"), alt = attribute(shot, "alt");
        become(shot, "figure", "data-shot", name);
        shot.getAsJsonArray("attributes").add(pair("aria-label", alt));
        shot.add("children", children);
    }

    /** What a widget's tag says, its words in the export's language, and which widget it is. */
    private static void widget(JsonObject widget, String tag) {
        JsonArray old = widget.getAsJsonArray("attributes");
        become(widget, "div", "data-live", tag.substring("residual:".length()).toLowerCase(java.util.Locale.ROOT));
        JsonArray attributes = widget.getAsJsonArray("attributes");
        for (JsonElement attribute : old) {
            JsonObject a = attribute.getAsJsonObject();
            attributes.add(pair("data-" + a.get("name").getAsString(), a.get("value").getAsString()));
        }
        for (String[] words : Live.webText(tag)) {
            attributes.add(pair(words[0], words[1]));
        }
    }

    private static void become(JsonObject node, String name, String attribute, String value) {
        node.addProperty("name", name);
        JsonArray attributes = new JsonArray();
        attributes.add(pair(attribute, value));
        node.add("attributes", attributes);
    }

    private static String attribute(JsonObject node, String name) {
        for (JsonElement attribute : node.getAsJsonArray("attributes")) {
            if (name.equals(attribute.getAsJsonObject().get("name").getAsString())) {
                return attribute.getAsJsonObject().get("value").getAsString();
            }
        }
        return "";
    }

    private static JsonObject pair(String name, String value) {
        JsonObject attribute = new JsonObject();
        attribute.addProperty("type", "mdxJsxAttribute");
        attribute.addProperty("name", name);
        attribute.addProperty("value", value);
        return attribute;
    }
}
