package dev.langchain4j.jackson3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import dev.langchain4j.exception.JsonReadException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;
import java.util.Date;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Jackson 3 defaults that {@link Jackson3Defaults} sets back to Jackson 2's values, and the
 * differences from Jackson 2 that are kept on purpose (listed in the Jackson 3 guide). Both are
 * tested so that a Jackson upgrade cannot change either without notice.
 */
class Jackson3DefaultsTest {

    // ---------- set back to Jackson 2 ----------

    @Test
    void constructors_are_not_detected_from_parameter_names() {
        // only observable for code compiled with -parameters, which this module's tests are not
        assertThat(Jackson3Defaults.pinJackson2Defaults(JsonMapper.builder())
                        .build()
                        .isEnabled(MapperFeature.DETECT_PARAMETER_NAMES))
                .isFalse();
    }

    @Test
    void a_one_argument_constructor_reads_a_plain_value() {
        assertThat(Json.fromJson("\"abc\"", UserId.class).value).isEqualTo("abc");
    }

    @Test
    void an_unknown_property_fails() {
        Jackson3ToolSpecificationJsonCodec codec = new Jackson3ToolSpecificationJsonCodec();

        assertThatThrownBy(() -> codec.fromJson("{\"name\":\"x\",\"unknown\":1}", Named.class))
                .isInstanceOf(JsonReadException.class);
    }

    @Test
    void a_provider_response_still_ignores_an_unknown_property() {
        Json.JsonCodec codec = ProviderJson.codec(ProviderJsonSpec.builder().build());

        assertThat(codec.fromJson("{\"name\":\"x\",\"unknown\":1}", Named.class).name)
                .isEqualTo("x");
    }

    // ---------- deliberately different from Jackson 2 ----------

    @Test
    void a_date_is_written_as_an_iso_string_and_read_from_either_form() {
        WithDate withDate = new WithDate();
        withDate.date = new Date(0);

        assertThat(Json.toJson(withDate)).isEqualTo("{\"date\":\"1970-01-01T00:00:00.000Z\"}");
        assertThat(Json.fromJson("{\"date\":0}", WithDate.class).date).isEqualTo(new Date(0));
    }

    @Test
    void an_object_without_properties_is_written_as_an_empty_object() {
        assertThat(Json.toJson(new Empty())).isEqualTo("{}");
    }

    @Test
    void an_empty_string_is_read_as_null_for_an_enum_only() {
        assertThat(Json.fromJson("{\"color\":\"\"}", WithColor.class).color).isNull();
        assertThatThrownBy(() -> Json.fromJson("\"\"", Named.class)).isInstanceOf(JsonReadException.class);
    }

    @Test
    void a_private_one_argument_constructor_needs_json_creator() {
        assertThatThrownBy(() -> Json.fromJson("\"abc\"", PrivateId.class)).isInstanceOf(JsonReadException.class);
        assertThat(Json.fromJson("\"abc\"", AnnotatedPrivateId.class).value).isEqualTo("abc");
    }

    @Test
    void a_property_with_a_one_letter_prefix_keeps_its_field_name() {
        assertThat(Json.toJson(new Point())).isEqualTo("{\"xValue\":1}");
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

    static class AnnotatedPrivateId {
        final String value;

        @JsonCreator
        private AnnotatedPrivateId(String value) {
            this.value = value;
        }
    }

    static class Named {
        public String name;
    }

    static class WithDate {
        public Date date;
    }

    static class Empty {}

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
