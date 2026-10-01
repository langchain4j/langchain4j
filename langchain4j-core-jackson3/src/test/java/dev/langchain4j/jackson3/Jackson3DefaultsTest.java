package dev.langchain4j.jackson3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.langchain4j.exception.JsonReadException;
import dev.langchain4j.exception.JsonWriteException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;
import java.lang.reflect.Constructor;
import java.time.Month;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DatatypeFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Jackson 3 defaults that {@link Jackson3Defaults} sets back to Jackson 2's values, and the
 * differences from Jackson 2 that are kept on purpose (listed in the Jackson 3 guide). Where the
 * Jackson 2 counterpart of a codec is on the classpath, the same input goes through both codecs,
 * so the tests check parity rather than what Jackson 2 is believed to do.
 */
class Jackson3DefaultsTest {

    private static final Json.JsonCodec JACKSON_2 = jackson2("dev.langchain4j.internal.JacksonJsonCodec");
    private static final Json.JsonCodec JACKSON_3 = new Jackson3JsonCodec();
    private static final List<Json.JsonCodec> BOTH = List.of(JACKSON_2, JACKSON_3);

    private static final Json.JsonCodec PROVIDER = ProviderJson.codec(ProviderJsonSpec.builder().build());

    // ---------- set back to Jackson 2 ----------

    @Test
    void an_enum_is_read_and_written_by_name() {
        for (Json.JsonCodec codec : BOTH) {
            assertThat(codec.fromJson("{\"priority\":\"HIGH\"}", Task.class).priority)
                    .as(name(codec))
                    .isEqualTo(Priority.HIGH);
            assertThat(codec.toJson(task(Priority.LOW))).as(name(codec)).isEqualTo("{\"priority\":\"LOW\"}");
        }
        assertThat(PROVIDER.fromJson("{\"priority\":\"HIGH\"}", Task.class).priority)
                .isEqualTo(Priority.HIGH);
        assertThat(PROVIDER.toJson(task(Priority.LOW))).isEqualTo("{\"priority\":\"LOW\"}");
    }

    @Test
    void an_enum_map_key_is_read_and_written_by_name() {
        for (Json.JsonCodec codec : BOTH) {
            ByPriority byPriority = new ByPriority();
            byPriority.owners = new EnumMap<>(Map.of(Priority.HIGH, "alice"));

            assertThat(codec.toJson(byPriority)).as(name(codec)).isEqualTo("{\"owners\":{\"HIGH\":\"alice\"}}");
            assertThat(codec.fromJson("{\"owners\":{\"LOW\":\"bob\"}}", ByPriority.class).owners)
                    .as(name(codec))
                    .containsEntry(Priority.LOW, "bob");
        }
    }

    @Test
    void json_alias_reads_a_former_to_string_value_and_json_value_keeps_to_string() {
        for (Json.JsonCodec codec : BOTH) {
            assertThat(codec.fromJson("{\"level\":\"Level high\"}", WithAliasedLevel.class).level)
                    .as(name(codec))
                    .isEqualTo(AliasedLevel.HIGH);
            assertThat(codec.fromJson("{\"level\":\"HIGH\"}", WithAliasedLevel.class).level)
                    .as(name(codec))
                    .isEqualTo(AliasedLevel.HIGH);

            WithValuedLevel valued = new WithValuedLevel();
            valued.level = ValuedLevel.LOW;
            assertThat(codec.toJson(valued)).as(name(codec)).isEqualTo("{\"level\":\"Level low\"}");
            assertThat(codec.fromJson("{\"level\":\"Level low\"}", WithValuedLevel.class).level)
                    .as(name(codec))
                    .isEqualTo(ValuedLevel.LOW);
        }
    }

    // this module's tests are compiled with -parameters, as Spring Boot and Quarkus applications are

    @Test
    void structured_output_detects_constructors_from_parameter_names() {
        assertThat(JACKSON_3.fromJson("{\"title\":\"x\",\"priority\":2}", ConstructorOnly.class).title)
                .isEqualTo("x");
        assertThat(JACKSON_3.fromJson("{\"value\":\"abc\"}", UserId.class).value).isEqualTo("abc");
    }

    @Test
    void other_codecs_do_not_detect_constructors_from_parameter_names() {
        Jackson3ToolSpecificationJsonCodec codec = new Jackson3ToolSpecificationJsonCodec();

        assertThat(codec.fromJson("\"abc\"", PublicUserId.class).value).isEqualTo("abc");
        assertThatThrownBy(() -> codec.fromJson("{\"title\":\"x\",\"priority\":2}", PublicConstructorOnly.class))
                .isInstanceOf(JsonReadException.class);
    }

    @Test
    void annotated_constructors_work_with_or_without_parameter_names() {
        for (Json.JsonCodec codec : BOTH) {
            assertThat(codec.fromJson("\"abc\"", DelegatingId.class).value).as(name(codec)).isEqualTo("abc");
            assertThat(codec.fromJson("{\"title\":\"x\",\"priority\":2}", AnnotatedConstructor.class).title)
                    .as(name(codec))
                    .isEqualTo("x");
        }
        Jackson3ToolSpecificationJsonCodec toolSpecificationCodec = new Jackson3ToolSpecificationJsonCodec();
        assertThat(toolSpecificationCodec.fromJson("\"abc\"", DelegatingId.class).value).isEqualTo("abc");
        assertThat(toolSpecificationCodec.fromJson("{\"title\":\"x\",\"priority\":2}", AnnotatedConstructor.class).title)
                .isEqualTo("x");
    }

    @Test
    void an_unknown_property_fails() {
        for (Json.JsonCodec codec : BOTH) {
            assertThatThrownBy(() -> codec.fromJson("{\"name\":\"x\",\"unknown\":1}", Named.class))
                    .as(name(codec))
                    .isInstanceOf(RuntimeException.class);
        }
        Jackson3ToolSpecificationJsonCodec toolSpecificationCodec = new Jackson3ToolSpecificationJsonCodec();
        assertThatThrownBy(() -> toolSpecificationCodec.fromJson("{\"name\":\"x\",\"unknown\":1}", Named.class))
                .isInstanceOf(JsonReadException.class);
    }

    @Test
    void a_provider_response_still_ignores_an_unknown_property() {
        assertThat(PROVIDER.fromJson("{\"name\":\"x\",\"unknown\":1}", Named.class).name)
                .isEqualTo("x");
    }

    @Test
    void an_object_without_properties_fails_instead_of_being_written_as_an_empty_object() {
        for (Json.JsonCodec codec : BOTH) {
            assertThatThrownBy(() -> codec.toJson(new Empty())).as(name(codec)).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void a_provider_request_value_with_nothing_to_write_fails_instead_of_being_sent_as_an_empty_object() {
        Map<String, Object> customParameters = Map.of("options", new PrivateFieldsOnly());

        assertThatThrownBy(() -> PROVIDER.toJson(customParameters)).isInstanceOf(JsonWriteException.class);
    }

    @Test
    void differs_from_jacksons_own_jackson_2_preset_only_where_deliberate() {
        // A Jackson upgrade that adds a setting to configureForJackson2() fails this test, so that
        // the new setting is either restored in Jackson3Defaults or added here on purpose.
        Map<String, String> deliberate = Map.of(
                "WRITE_DATES_AS_TIMESTAMPS", "dates and times are written as ISO-8601 strings",
                "WRITE_DURATIONS_AS_TIMESTAMPS", "durations are written as ISO-8601 strings",
                "WRITE_UTC_AS_OFFSET", "goes with ISO-8601 dates",
                "ONE_BASED_MONTHS", "a month number is read as 1 for January",
                "FIX_FIELD_NAME_UPPER_CASE_PREFIX", "xValue is written once, as \"xValue\"",
                "STRIP_TRAILING_BIGDECIMAL_ZEROES", "only affects JsonNode trees, which are not read with BigDecimal");

        assertThat(featuresThatDiffer(
                        Jackson3Defaults.pinJackson2Defaults(JsonMapper.builder()).build(),
                        JsonMapper.builder().configureForJackson2().build()))
                .containsExactlyInAnyOrderElementsOf(deliberate.keySet());
    }

    // ---------- deliberately different from Jackson 2 ----------

    @Test
    void a_date_is_written_as_an_iso_string_and_read_from_either_form() {
        WithDate withDate = new WithDate();
        withDate.date = new Date(0);

        assertThat(JACKSON_3.toJson(withDate)).isEqualTo("{\"date\":\"1970-01-01T00:00:00.000Z\"}");
        assertThat(JACKSON_3.fromJson("{\"date\":0}", WithDate.class).date).isEqualTo(new Date(0));
    }

    @Test
    void a_month_is_written_as_a_number_starting_at_one_and_read_by_name() {
        WithMonth withMonth = new WithMonth();
        withMonth.month = Month.JANUARY;

        assertThat(JACKSON_3.toJson(withMonth)).isEqualTo("{\"month\":1}");
        assertThat(JACKSON_3.fromJson("{\"month\":1}", WithMonth.class).month).isEqualTo(Month.JANUARY);
        assertThat(JACKSON_3.fromJson("{\"month\":\"JANUARY\"}", WithMonth.class).month)
                .isEqualTo(Month.JANUARY);
    }

    @Test
    void an_empty_string_is_read_as_null_for_an_enum_only() {
        assertThat(JACKSON_3.fromJson("{\"color\":\"\"}", WithColor.class).color).isNull();
        assertThatThrownBy(() -> JACKSON_3.fromJson("\"\"", Named.class)).isInstanceOf(JsonReadException.class);
    }

    @Test
    void structured_output_prefers_a_constructor_with_arguments_over_the_no_argument_one() {
        assertThat(JACKSON_3.fromJson("{\"title\":\"x\"}", BothConstructors.class).title)
                .isEqualTo("from constructor: x");
    }

    @Test
    void a_private_one_argument_constructor_needs_json_creator() {
        Jackson3ToolSpecificationJsonCodec codec = new Jackson3ToolSpecificationJsonCodec();

        assertThatThrownBy(() -> codec.fromJson("\"abc\"", PrivateId.class)).isInstanceOf(JsonReadException.class);
        assertThat(codec.fromJson("\"abc\"", DelegatingId.class).value).isEqualTo("abc");
    }

    @Test
    void a_property_with_a_one_letter_prefix_keeps_its_field_name() {
        assertThat(JACKSON_3.toJson(new Point())).isEqualTo("{\"xValue\":1}");
        assertThat(JACKSON_2.toJson(new Point())).isEqualTo("{\"xValue\":1,\"xvalue\":1}");
        assertThatThrownBy(() -> JACKSON_3.fromJson("{\"xValue\":1,\"xvalue\":1}", Point.class))
                .isInstanceOf(JsonReadException.class);
    }

    private static Set<String> featuresThatDiffer(ObjectMapper ours, ObjectMapper preset) {
        Set<String> differ = new TreeSet<>();
        for (MapperFeature feature : MapperFeature.values()) {
            if (ours.isEnabled(feature) != preset.isEnabled(feature)) differ.add(feature.name());
        }
        for (DeserializationFeature feature : DeserializationFeature.values()) {
            if (ours.isEnabled(feature) != preset.isEnabled(feature)) differ.add(feature.name());
        }
        for (SerializationFeature feature : SerializationFeature.values()) {
            if (ours.isEnabled(feature) != preset.isEnabled(feature)) differ.add(feature.name());
        }
        List<DatatypeFeature> datatypeFeatures = new ArrayList<>();
        datatypeFeatures.addAll(List.of(EnumFeature.values()));
        datatypeFeatures.addAll(List.of(DateTimeFeature.values()));
        datatypeFeatures.addAll(List.of(JsonNodeFeature.values()));
        for (DatatypeFeature feature : datatypeFeatures) {
            if (ours.deserializationConfig().isEnabled(feature)
                    != preset.deserializationConfig().isEnabled(feature)) {
                differ.add(((Enum<?>) feature).name());
            }
        }
        return differ;
    }

    private static Json.JsonCodec jackson2(String className) {
        try {
            Constructor<?> constructor = Class.forName(className).getDeclaredConstructor();
            constructor.setAccessible(true);
            return (Json.JsonCodec) constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String name(Json.JsonCodec codec) {
        return codec.getClass().getSimpleName();
    }

    private static Task task(Priority priority) {
        Task task = new Task();
        task.priority = priority;
        return task;
    }

    enum Priority {
        HIGH,
        LOW;

        @Override
        public String toString() {
            return "Priority " + name().toLowerCase(Locale.ROOT);
        }
    }

    static class Task {
        public Priority priority;
    }

    static class ByPriority {
        public Map<Priority, String> owners;
    }

    enum AliasedLevel {
        @JsonAlias("Level high")
        HIGH,
        @JsonAlias("Level low")
        LOW;

        @Override
        public String toString() {
            return "Level " + name().toLowerCase(Locale.ROOT);
        }
    }

    static class WithAliasedLevel {
        public AliasedLevel level;
    }

    enum ValuedLevel {
        HIGH,
        LOW;

        @JsonValue
        @Override
        public String toString() {
            return "Level " + name().toLowerCase(Locale.ROOT);
        }
    }

    static class WithValuedLevel {
        public ValuedLevel level;
    }

    static class UserId {
        final String value;

        UserId(String value) {
            this.value = value;
        }
    }

    static class PrivateId {
        final String value;

        private PrivateId(String value) {
            this.value = value;
        }
    }

    static class DelegatingId {
        final String value;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        private DelegatingId(String value) {
            this.value = value;
        }
    }

    public static class PublicUserId {
        public final String value;

        public PublicUserId(String value) {
            this.value = value;
        }
    }

    public static class PublicConstructorOnly {
        public final String title;
        public final int priority;

        public PublicConstructorOnly(String title, int priority) {
            this.title = title;
            this.priority = priority;
        }
    }

    static class ConstructorOnly {
        final String title;
        final int priority;

        ConstructorOnly(String title, int priority) {
            this.title = title;
            this.priority = priority;
        }
    }

    static class AnnotatedConstructor {
        final String title;
        final int priority;

        @JsonCreator
        AnnotatedConstructor(@JsonProperty("title") String title, @JsonProperty("priority") int priority) {
            this.title = title;
            this.priority = priority;
        }
    }

    static class BothConstructors {
        public String title;

        BothConstructors() {}

        BothConstructors(String title) {
            this.title = "from constructor: " + title;
        }
    }

    static class Named {
        public String name;
    }

    static class WithDate {
        public Date date;
    }

    static class WithMonth {
        public Month month;
    }

    static class Empty {}

    static class PrivateFieldsOnly {
        private String mode = "fast";
    }

    enum Color {
        RED
    }

    static class WithColor {
        public Color color;
    }

    static class Point {
        private int xValue = 1;

        public int getXValue() {
            return xValue;
        }
    }
}
