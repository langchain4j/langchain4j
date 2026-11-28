package dev.langchain4j.jackson3;

import static dev.langchain4j.data.message.AiMessage.GENERATED_AUDIO_KEY;
import static dev.langchain4j.data.message.AiMessage.GENERATED_IMAGES_KEY;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Jackson 3 twin of {@code dev.langchain4j.data.message.AiMessageAttributesDeserializer}.
 *
 * <p>Reads the attributes of an {@link dev.langchain4j.data.message.AiMessage} back with the type
 * that LangChain4j's own attribute keys are documented to hold, so that
 * {@link dev.langchain4j.data.message.AiMessage#images()} and
 * {@link dev.langchain4j.data.message.AiMessage#audios()} keep working across a JSON round trip. The
 * written JSON is unchanged, so a message persisted by an earlier version still reads.
 */
class AiMessageAttributesDeserializer extends ValueDeserializer<Map<String, Object>> {

    private static final Map<String, Class<?>> ATTRIBUTE_ELEMENT_TYPES = Map.of(
            GENERATED_IMAGES_KEY, Image.class,
            GENERATED_AUDIO_KEY, Audio.class);

    @Override
    public Map<String, Object> deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        JsonNode attributesNode = context.readTree(parser);
        if (!attributesNode.isObject()) {
            return context.reportInputMismatch(Map.class, "Expected an object, but got: %s", attributesNode);
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> attribute : attributesNode.properties()) {
            attributes.put(attribute.getKey(), value(context, attribute.getKey(), attribute.getValue()));
        }
        return attributes;
    }

    private static Object value(DeserializationContext context, String key, JsonNode value) throws JacksonException {
        Class<?> elementType = ATTRIBUTE_ELEMENT_TYPES.get(key);
        if (elementType == null || !value.isArray()) {
            return context.readTreeAsValue(value, Object.class);
        }

        List<Object> elements = new ArrayList<>();
        for (JsonNode element : value) {
            elements.add(context.readTreeAsValue(element, elementType));
        }
        return elements;
    }
}
