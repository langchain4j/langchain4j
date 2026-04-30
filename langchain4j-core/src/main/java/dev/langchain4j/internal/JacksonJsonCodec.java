package dev.langchain4j.internal;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.PropertyAccessor.FIELD;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS;
import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;
import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static java.time.format.DateTimeFormatter.ISO_OFFSET_TIME;
import static java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import dev.langchain4j.Internal;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A JSON codec implementation using Jackson for serialization and deserialization.
 * Provides methods to convert objects to their JSON representation and parse JSON strings into objects.
 * Customizes the behavior of the Jackson {@link ObjectMapper} to support serialization and deserialization
 * of Java 8 date/time types such as {@link LocalDate}, {@link LocalTime}, and {@link LocalDateTime}.
 */
@Internal
class JacksonJsonCodec implements Json.JsonCodec {

    private final ObjectMapper objectMapper;

    private static ObjectMapper createObjectMapper() {

        SimpleModule module = new SimpleModule("langchain4j-module");

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
                    int second = Optional.ofNullable(node.get("second"))
                            .map(JsonNode::asInt)
                            .orElse(0);
                    int nano = Optional.ofNullable(node.get("nano"))
                            .map(JsonNode::asInt)
                            .orElse(0);
                    return LocalTime.of(hour, minute, second, nano);
                } else {
                    return LocalTime.parse(node.asText(), ISO_LOCAL_TIME);
                }
            }
        });

        module.addSerializer(LocalDateTime.class, new StdSerializer<>(LocalDateTime.class) {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider)
                    throws IOException {
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
                    int second = Optional.ofNullable(time.get("second"))
                            .map(JsonNode::asInt)
                            .orElse(0);
                    int nano = Optional.ofNullable(time.get("nano"))
                            .map(JsonNode::asInt)
                            .orElse(0);
                    return LocalDateTime.of(year, month, day, hour, minute, second, nano);
                } else {
                    return LocalDateTime.parse(node.asText(), ISO_LOCAL_DATE_TIME);
                }
            }
        });

        // String-only handlers for the remaining java.time types declared as ISO-8601 strings in
        // dev.langchain4j.internal.JsonSchemaElementUtils#DEFAULT_TIME_DESCRIPTIONS. Without
        // these, deserializing a tool argument or structured output containing one of these types
        // fails because jackson-datatype-jsr310 is not a declared dependency.
        addIsoStringHandlers(module, Instant.class, Instant::parse);
        addIsoStringHandlers(module, OffsetDateTime.class, s -> OffsetDateTime.parse(s, ISO_OFFSET_DATE_TIME));
        addIsoStringHandlers(module, OffsetTime.class, s -> OffsetTime.parse(s, ISO_OFFSET_TIME));
        addIsoStringHandlers(module, ZonedDateTime.class, s -> ZonedDateTime.parse(s, ISO_ZONED_DATE_TIME));
        addIsoStringHandlers(module, Duration.class, Duration::parse);
        addIsoStringHandlers(module, Period.class, Period::parse);
        addIsoStringHandlers(module, Year.class, Year::parse);
        addIsoStringHandlers(module, YearMonth.class, YearMonth::parse);
        addIsoStringHandlers(module, MonthDay.class, MonthDay::parse);
        addIsoStringHandlers(module, ZoneId.class, ZoneId::of);
        addIsoStringHandlers(module, ZoneOffset.class, ZoneOffset::of);

        ObjectMapper mapper = JsonMapper.builder()
                .visibility(FIELD, ANY)
                .disable(INDENT_OUTPUT) // disabled on purpose to save tokens when sending tool results to LLM
                .enable(FAIL_ON_UNKNOWN_PROPERTIES) // enabled on purpose to prevent issues caused by LLM hallucinations
                .enable(ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build()
                .findAndRegisterModules()
                .registerModule(module);

        // Make sealed interfaces/classes deserializable as polymorphic types without the user
        // having to add @JsonTypeInfo+@JsonSubTypes. We synthesize equivalent metadata via a
        // custom AnnotationIntrospector consulted ahead of Jackson's default one.
        mapper.setAnnotationIntrospector(AnnotationIntrospectorPair.pair(
                new SealedTypePolymorphicIntrospector(),
                mapper.getDeserializationConfig().getAnnotationIntrospector()));
        return mapper;
    }

    private static <T> void addIsoStringHandlers(SimpleModule module, Class<T> type, Function<String, T> parser) {
        module.addSerializer(type, new StdSerializer<T>(type) {
            @Override
            public void serialize(T value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeString(value.toString());
            }
        });
        module.addDeserializer(type, new JsonDeserializer<T>() {
            @Override
            public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return parser.apply(p.getValueAsString());
            }
        });
    }

    /**
     * Constructs a JacksonJsonCodec instance with the provided ObjectMapper.
     *
     * @param objectMapper the ObjectMapper to use for JSON serialization and deserialization.
     */
    public JacksonJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Constructs a JacksonJsonCodec instance with a default ObjectMapper.
     * The default ObjectMapper is configured with custom serializers and deserializers
     * for Java 8 date/time types such as LocalDate, LocalTime, and LocalDateTime.
     * It also registers other modules found on the classpath
     * and throws exceptions for unknown properties to improve handling of unexpected input.
     */
    public JacksonJsonCodec() {
        this(createObjectMapper());
    }

    @Override
    public String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, Type type) {
        try {
            return objectMapper.readValue(json, objectMapper.constructType(type));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the ObjectMapper instance used for JSON processing.
     *
     * @return the ObjectMapper instance.
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Synthesizes {@code @JsonTypeInfo} + {@code @JsonSubTypes} metadata for sealed types that
     * carry no Jackson polymorphism annotations of their own. With this introspector consulted
     * ahead of Jackson's default one, sealed bases dispatch natively via the same discriminator
     * langchain4j puts in the schema — no custom deserializer needed.
     */
    private static final class SealedTypePolymorphicIntrospector extends NopAnnotationIntrospector {

        @Override
        public TypeResolverBuilder<?> findTypeResolver(
                MapperConfig<?> config, AnnotatedClass ac, com.fasterxml.jackson.databind.JavaType baseType) {
            Class<?> raw = ac.getRawType();
            if (!shouldHandle(raw)) {
                return null;
            }
            StdTypeResolverBuilder builder = new StdTypeResolverBuilder()
                    .init(JsonTypeInfo.Id.NAME, null)
                    .inclusion(JsonTypeInfo.As.PROPERTY)
                    .typeProperty(PolymorphicTypes.discriminatorPropertyName(raw))
                    .typeIdVisibility(false);
            return builder;
        }

        @Override
        public List<NamedType> findSubtypes(com.fasterxml.jackson.databind.introspect.Annotated a) {
            Class<?> raw = a.getRawType();
            if (!shouldHandle(raw)) {
                return null;
            }
            return PolymorphicTypes.findConcreteSubtypes(raw).stream()
                    .map(sub -> new NamedType(sub, PolymorphicTypes.discriminatorValue(raw, sub)))
                    .toList();
        }

        private static boolean shouldHandle(Class<?> raw) {
            // Step in for any polymorphic base that doesn't already declare its own type-info
            // strategy via @JsonTypeInfo. This covers both sealed types (no annotations) and
            // types that only use @JsonSubTypes for subtype enumeration.
            // Skip JDK types: some are sealed in JDK 17+ (e.g. java.time.ZoneId permits
            // ZoneOffset/ZoneRegion) but we register custom (de)serializers for them and
            // don't want polymorphic dispatch.
            return raw.getAnnotation(JsonTypeInfo.class) == null
                    && !isJdkType(raw)
                    && PolymorphicTypes.isPolymorphic(raw);
        }

        private static boolean isJdkType(Class<?> raw) {
            if (raw.getPackage() == null) {
                return false;
            }
            String name = raw.getPackage().getName();
            return name.startsWith("java.")
                    || name.startsWith("javax.")
                    || name.startsWith("jdk.")
                    || name.startsWith("sun.")
                    || name.startsWith("com.sun.");
        }
    }
}
