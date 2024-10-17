package dev.langchain4j.model.jlama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

class JacksonJsonCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .registerModule(new JavaTimeModule())
            .registerModule(new SimpleModule()
                    .addSerializer(LocalDate.class, new LocalDateSerializer())
                    .addDeserializer(LocalDate.class, new LocalDateDeserializer())
                    .addSerializer(LocalTime.class, new LocalTimeSerializer())
                    .addDeserializer(LocalTime.class, new LocalTimeDeserializer())
                    .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer())
                    .addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer())
            );

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            if (Map.class.isAssignableFrom(type)) {
                return OBJECT_MAPPER.readValue(json, OBJECT_MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            }
            return OBJECT_MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new JacksonProcessingException("Error converting JSON to object", e);
        }
    }

    private static class LocalDateSerializer extends com.fasterxml.jackson.databind.JsonSerializer<LocalDate> {
        @Override
        public void serialize(LocalDate value, com.fasterxml.jackson.core.JsonGenerator gen, com.fasterxml.jackson.databind.SerializerProvider serializers) throws IOException {
            gen.writeString(value.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
    }

    private static class LocalDateDeserializer extends com.fasterxml.jackson.databind.JsonDeserializer<LocalDate> {
        @Override
        public LocalDate deserialize(com.fasterxml.jackson.core.JsonParser p, com.fasterxml.jackson.databind.DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    private static class LocalTimeSerializer extends com.fasterxml.jackson.databind.JsonSerializer<LocalTime> {
        @Override
        public void serialize(LocalTime value, com.fasterxml.jackson.core.JsonGenerator gen, com.fasterxml.jackson.databind.SerializerProvider serializers) throws IOException {
            gen.writeString(value.format(DateTimeFormatter.ISO_LOCAL_TIME));
        }
    }

    private static class LocalTimeDeserializer extends com.fasterxml.jackson.databind.JsonDeserializer<LocalTime> {
        @Override
        public LocalTime deserialize(com.fasterxml.jackson.core.JsonParser p, com.fasterxml.jackson.databind.DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();
            return LocalTime.parse(value, DateTimeFormatter.ISO_LOCAL_TIME);
        }
    }

    private static class LocalDateTimeSerializer extends com.fasterxml.jackson.databind.JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, com.fasterxml.jackson.core.JsonGenerator gen, com.fasterxml.jackson.databind.SerializerProvider serializers) throws IOException {
            gen.writeString(value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
    }

    private static class LocalDateTimeDeserializer extends com.fasterxml.jackson.databind.JsonDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(com.fasterxml.jackson.core.JsonParser p, com.fasterxml.jackson.databind.DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
}
