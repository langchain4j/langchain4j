package dev.langchain4j.internal;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecificationJsonCodec;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.spi.agent.tool.ToolSpecificationJsonCodecFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles JSON serialization and deserialization of {@link ToolSpecification}.
 *
 * @see JsonSchemaElementJsonUtils
 */
@Internal
public class ToolSpecificationJsonUtils {

    private static final ToolSpecificationJsonCodec CODEC = loadCodec();

    private static ToolSpecificationJsonCodec loadCodec() {
        for (ToolSpecificationJsonCodecFactory factory : loadFactories(ToolSpecificationJsonCodecFactory.class)) {
            return factory.create();
        }
        return new JacksonToolSpecificationJsonCodec();
    }

    private ToolSpecificationJsonUtils() {}

    /**
     * Serializes a {@link ToolSpecification} to a JSON string.
     */
    public static String toJson(ToolSpecification toolSpecification) {
        ensureNotNull(toolSpecification, "toolSpecification");

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", toolSpecification.name());
        if (toolSpecification.description() != null) {
            map.put("description", toolSpecification.description());
        }
        if (toolSpecification.parameters() != null) {
            map.put("parameters", JsonSchemaElementJsonUtils.toMap(toolSpecification.parameters()));
        }
        if (toolSpecification.metadata() != null
                && !toolSpecification.metadata().isEmpty()) {
            map.put("metadata", toolSpecification.metadata());
        }
        return CODEC.toJson(map);
    }

    /**
     * Deserializes a {@link ToolSpecification} from a JSON string.
     */
    @SuppressWarnings("unchecked")
    public static ToolSpecification fromJson(String json) {
        ensureNotNull(json, "json");

        Map<String, Object> map = CODEC.fromJson(json, Map.class);

        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(optionalString(map, "name"))
                .description(optionalString(map, "description"));

        Object parametersObj = map.get("parameters");
        if (parametersObj instanceof Map) {
            Map<String, Object> parametersMap = (Map<String, Object>) parametersObj;
            JsonSchemaElement element = JsonSchemaElementJsonUtils.fromMap(parametersMap);
            if (element instanceof JsonObjectSchema objectSchema) {
                builder.parameters(objectSchema);
            } else {
                throw new IllegalArgumentException("\"parameters\" must be a JSON object schema, but was: "
                        + element.getClass().getSimpleName());
            }
        } else if (parametersObj != null) {
            throw new IllegalArgumentException("\"parameters\" must be a JSON object, but was: "
                    + parametersObj.getClass().getSimpleName());
        }

        Object metadataObj = map.get("metadata");
        if (metadataObj instanceof Map) {
            builder.metadata((Map<String, Object>) metadataObj);
        } else if (metadataObj != null) {
            throw new IllegalArgumentException("\"metadata\" must be a JSON object, but was: "
                    + metadataObj.getClass().getSimpleName());
        }

        return builder.build();
    }

    private static String optionalString(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("\"" + field + "\" must be a string, but was: "
                    + value.getClass().getSimpleName());
        }
        return (String) value;
    }
}
