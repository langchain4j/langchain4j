package dev.langchain4j.internal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.PropertyAccessor.FIELD;
import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;

class JacksonJsonCodec implements Json.JsonCodec { // TODO move to main

    // TODO check core and main for any "gson"

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {

        SimpleModule module = new SimpleModule();

        module.addSerializer(LocalDate.class, new StdSerializer<>(LocalDate.class) {
            @Override
            public void serialize(LocalDate value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeString(value.format(ISO_LOCAL_DATE));
            }
        });

        module.addDeserializer(LocalDate.class, new JsonDeserializer<>() {
            @Override
            public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonNode node = p.getCodec().readTree(p);
                if (node.isObject()) {
                    int year = node.get("year").asInt();
                    int month = node.get("month").asInt();
                    int day = node.get("day").asInt();
                    return LocalDate.of(year, month, day);
                } else {
                    return LocalDate.parse(node.asText(), ISO_LOCAL_DATE);
                }
            }
        });

        module.addSerializer(LocalTime.class, new StdSerializer<>(LocalTime.class) {
            @Override
            public void serialize(LocalTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeString(value.format(ISO_LOCAL_TIME));
            }
        });

        module.addDeserializer(LocalTime.class, new JsonDeserializer<>() {
            @Override
            public LocalTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonNode node = p.getCodec().readTree(p);
                if (node.isObject()) {
                    int hour = node.get("hour").asInt();
                    int minute = node.get("minute").asInt();
                    int second = node.get("second").asInt();
                    int nano = node.get("nano").asInt();
                    return LocalTime.of(hour, minute, second, nano);
                } else {
                    return LocalTime.parse(node.asText(), ISO_LOCAL_TIME);
                }
            }
        });

        module.addSerializer(LocalDateTime.class, new StdSerializer<>(LocalDateTime.class) {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeString(value.format(ISO_LOCAL_DATE_TIME));
            }
        });

        module.addDeserializer(LocalDateTime.class, new JsonDeserializer<>() {
            @Override
            public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonNode node = p.getCodec().readTree(p);
                if (node.isObject()) {
                    JsonNode date = node.get("date");
                    int year = date.get("year").asInt();
                    int month = date.get("month").asInt();
                    int day = date.get("day").asInt();
                    JsonNode time = node.get("time");
                    int hour = time.get("hour").asInt();
                    int minute = time.get("minute").asInt();
                    int second = time.get("second").asInt();
                    int nano = time.get("nano").asInt();
                    return LocalDateTime.of(year, month, day, hour, minute, second, nano);
                } else {
                    return LocalDateTime.parse(node.asText(), ISO_LOCAL_DATE_TIME);
                }
            }
        });

        return new ObjectMapper()
                .setVisibility(FIELD, ANY)
                .registerModule(module)
                .enable(INDENT_OUTPUT);
    }

    @Override
    public String toJson(Object o) {
        try {
            return OBJECT_MAPPER.writeValueAsString(o);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, Type type) {
        try {
            return OBJECT_MAPPER.readValue(json, OBJECT_MAPPER.constructType(type));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
