package dev.langchain4j.data.message;

import com.google.gson.*;

import java.lang.reflect.Type;

class GsonContentAdapter implements JsonDeserializer<Content>, JsonSerializer<Content> {

    private static final Gson GSON = new Gson();

    private static final String CONTENT_TYPE = "type"; // do not change, will break backward compatibility!

    @Override
    public JsonElement serialize(Content content, Type ignored, JsonSerializationContext context) {
        JsonObject contentJsonObject = GSON.toJsonTree(content).getAsJsonObject();
        contentJsonObject.addProperty(CONTENT_TYPE, content.type().toString());
        return contentJsonObject;
    }

    @Override
    public Content deserialize(JsonElement contentJsonElement, Type ignored, JsonDeserializationContext context) throws JsonParseException {
        String contentTypeString = contentJsonElement.getAsJsonObject().get(CONTENT_TYPE).getAsString();
        ContentType contentType = ContentType.valueOf(contentTypeString);
        return GSON.fromJson(contentJsonElement, contentType.getContentClass());
    }
}