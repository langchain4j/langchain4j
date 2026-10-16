package dev.langchain4j.data.message;

import static dev.langchain4j.data.message.AiMessage.GENERATED_AUDIO_KEY;
import static dev.langchain4j.data.message.AiMessage.GENERATED_IMAGES_KEY;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the attributes of an {@link AiMessage}, giving the values that LangChain4j stores under its
 * own keys the type those keys are documented to hold.
 *
 * <p>{@link AiMessage#GENERATED_IMAGES_KEY} and {@link AiMessage#GENERATED_AUDIO_KEY} hold lists of
 * objects, and an attribute map - a plain {@code Map<String, Object>} - carries nothing that tells
 * one object from another. Read plainly they come back as {@code LinkedHashMap}s, which
 * {@link AiMessage#images()} and {@link AiMessage#audios()} then cannot return as {@link Image}s and
 * {@link Audio}s, and which also make a message read back unequal to the one that was written.
 *
 * <p>The written JSON is unchanged: only the read side knows what the keys mean, so a message
 * persisted by an earlier version still reads.
 *
 * <p>This deserializer only runs when the attributes are read as an untyped map. A mapper that
 * writes type information into the JSON - as the state codec does, since it has to read chat
 * messages stored as plain state values - carries the element types itself, and Jackson reads the
 * values with its own typed deserializer instead of this one.
 */
class AiMessageAttributesDeserializer extends JsonDeserializer<Map<String, Object>> {

    private static final Map<String, Class<?>> ATTRIBUTE_ELEMENT_TYPES = Map.of(
            GENERATED_IMAGES_KEY, Image.class,
            GENERATED_AUDIO_KEY, Audio.class);

    @Override
    public Map<String, Object> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectCodec codec = parser.getCodec();
        JsonNode attributesNode = codec.readTree(parser);
        if (!attributesNode.isObject()) {
            return context.reportInputMismatch(Map.class, "Expected an object, but got: %s", attributesNode);
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> attribute : attributesNode.properties()) {
            attributes.put(attribute.getKey(), value(codec, attribute.getKey(), attribute.getValue()));
        }
        return attributes;
    }

    private static Object value(ObjectCodec codec, String key, JsonNode value) throws IOException {
        Class<?> elementType = ATTRIBUTE_ELEMENT_TYPES.get(key);
        if (elementType == null || !value.isArray()) {
            return codec.treeToValue(value, Object.class);
        }

        List<Object> elements = new ArrayList<>();
        for (JsonNode element : value) {
            elements.add(codec.treeToValue(element, elementType));
        }
        return elements;
    }
}
