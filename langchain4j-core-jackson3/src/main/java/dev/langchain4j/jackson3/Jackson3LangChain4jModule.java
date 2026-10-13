package dev.langchain4j.jackson3;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Date/time handling matching the Jackson 2 codec: values are written as ISO strings, and read
 * from either an ISO string or the field-wise object form that LLMs often produce, such as
 * {@code {"year": 2026, "month": 8, "day": 20}}.
 */
final class Jackson3LangChain4jModule {

    private Jackson3LangChain4jModule() {}

    static SimpleModule create() {
        SimpleModule module = new SimpleModule("langchain4j-module");

        module.addSerializer(LocalDate.class, new StdSerializer<>(LocalDate.class) {
            @Override
            public void serialize(LocalDate value, JsonGenerator gen, SerializationContext ctxt) {
                gen.writeString(value.format(ISO_LOCAL_DATE));
            }
        });

        module.addDeserializer(LocalDate.class, new ValueDeserializer<>() {
            @Override
            public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) {
                JsonNode node = ctxt.readTree(p);
                if (node.isObject()) {
                    return LocalDate.of(asInt(node, "year"), asInt(node, "month"), asInt(node, "day"));
                }
                return LocalDate.parse(node.asString(), ISO_LOCAL_DATE);
            }
        });

        module.addSerializer(LocalTime.class, new StdSerializer<>(LocalTime.class) {
            @Override
            public void serialize(LocalTime value, JsonGenerator gen, SerializationContext ctxt) {
                gen.writeString(value.format(ISO_LOCAL_TIME));
            }
        });

        module.addDeserializer(LocalTime.class, new ValueDeserializer<>() {
            @Override
            public LocalTime deserialize(JsonParser p, DeserializationContext ctxt) {
                JsonNode node = ctxt.readTree(p);
                if (node.isObject()) {
                    return toLocalTime(node);
                }
                return LocalTime.parse(node.asString(), ISO_LOCAL_TIME);
            }
        });

        module.addSerializer(LocalDateTime.class, new StdSerializer<>(LocalDateTime.class) {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) {
                gen.writeString(value.format(ISO_LOCAL_DATE_TIME));
            }
        });

        module.addDeserializer(LocalDateTime.class, new ValueDeserializer<>() {
            @Override
            public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
                JsonNode node = ctxt.readTree(p);
                if (node.isObject()) {
                    JsonNode date = node.get("date");
                    JsonNode time = node.get("time");
                    return LocalDateTime.of(
                            LocalDate.of(asInt(date, "year"), asInt(date, "month"), asInt(date, "day")),
                            toLocalTime(time));
                }
                return LocalDateTime.parse(node.asString(), ISO_LOCAL_DATE_TIME);
            }
        });

        return module;
    }

    private static LocalTime toLocalTime(JsonNode node) {
        return LocalTime.of(
                asInt(node, "hour"), asInt(node, "minute"), asIntOrZero(node, "second"), asIntOrZero(node, "nano"));
    }

    private static int asInt(JsonNode node, String field) {
        return node.get(field).asInt();
    }

    private static int asIntOrZero(JsonNode node, String field) {
        return Optional.ofNullable(node.get(field)).map(JsonNode::asInt).orElse(0);
    }
}
