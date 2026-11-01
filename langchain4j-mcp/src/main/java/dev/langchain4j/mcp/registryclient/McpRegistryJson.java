package dev.langchain4j.mcp.registryclient;

import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;
import java.time.LocalDateTime;

@Internal
public class McpRegistryJson {

    private McpRegistryJson() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    /**
     * The registry model classes keep their fields private and expose public getters, which is what
     * lets a codec see them: a wire codec detects properties from accessors, not from fields. A new
     * field without a public getter, or one named other than {@code getX}/{@code isX}, would be
     * skipped silently and read as null - so such a field has to name itself with
     * {@code @JsonProperty}, as {@code McpRegistryPong} does. {@code McpRegistryModelTest} enforces
     * this.
     */
    private static final Json.JsonCodec CODEC =
            ProviderJson.codec(ProviderJsonSpec.builder().prettyPrint(true).build());

    public static <T> T fromJson(String json, Class<T> type) {
        return CODEC.fromJson(json, type);
    }

    /**
     * The registry sends UTC timestamps such as {@code 2025-09-29T12:00:00Z}. They are parsed here
     * rather than by a JSON-library-specific deserializer, so that any codec reads them.
     */
    public static LocalDateTime parseTimestamp(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return LocalDateTime.parse(value.toString(), ISO_DATE_TIME);
    }
}
