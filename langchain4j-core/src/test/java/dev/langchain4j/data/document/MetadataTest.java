package dev.langchain4j.data.document;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.util.Collections.singletonMap;

class MetadataTest implements WithAssertions {

    @Test
    public void test_add_get_put() {
        Metadata m = new Metadata();

        assertThat(m.getString("foo")).isNull();
        m.put("foo", "bar");
        assertThat(m.getString("foo")).isEqualTo("bar");

        m.put("xyz", 2);
        assertThat(m.getInteger("xyz").toString()).isEqualTo("2");
    }

    @Test
    public void test_map_constructor_copies() {
        Map<String, String> source = new HashMap<>();
        source.put("foo", "bar");

        Metadata m = new Metadata(source);
        Map<String, String> sourceCopy = new HashMap<>(source);

        source.put("baz", "qux");
        assertThat(m.toMap()).isEqualTo(sourceCopy);
    }

    @Test
    public void test_toString() {
        Metadata m = new Metadata();
        m.put("foo", "bar");
        m.put("baz", "qux");
        assertThat(m.toString()).isEqualTo("Metadata { metadata = {foo=bar, baz=qux} }");
    }

    @Test
    public void test_equals_hash() {
        Metadata m1 = new Metadata();
        Metadata m2 = new Metadata();
        m1.put("foo", "bar");
        m2.put("foo", "bar");

        Metadata m3 = new Metadata();
        Metadata m4 = new Metadata();
        m3.put("different", "value");
        m4.put("different", "value");

        assertThat(m1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(m2)
                .hasSameHashCodeAs(m2);

        assertThat(m1)
                .isNotEqualTo(m3)
                .doesNotHaveSameHashCodeAs(m3);

        assertThat(m3)
                .isEqualTo(m4)
                .hasSameHashCodeAs(m4);
    }

    @Test
    public void test_copy() {
        Metadata m1 = new Metadata();
        m1.put("foo", "bar");
        Metadata m2 = m1.copy();
        assertThat(m1).isEqualTo(m2);
        m1.put("foo", "baz");
        assertThat(m1).isNotEqualTo(m2);
    }

    @Test
    public void test_builders() {
        Map<String, String> emptyMap = new HashMap<>();
        Map<String, String> map = new HashMap<>();
        map.put("foo", "bar");
        map.put("baz", "qux");

        assertThat(new Metadata())
                .isEqualTo(new Metadata(emptyMap));

        assertThat(Metadata.from(map))
                .isEqualTo(new Metadata().put("foo", "bar").put("baz", "qux"));

        assertThat(Metadata.from("foo", "bar"))
                .isEqualTo(new Metadata().put("foo", "bar"));

        assertThat(Metadata.metadata("foo", "bar"))
                .isEqualTo(new Metadata().put("foo", "bar"));

        assertThat(Metadata.from("foo", 2))
                .isEqualTo(new Metadata().put("foo", "2"));
        assertThat(Metadata.metadata("foo", 2))
                .isEqualTo(new Metadata().put("foo", "2"));
    }

    @Test
    public void test_remove() {
        Metadata m1 = new Metadata();
        m1.put("foo", "bar");
        m1.put("baz", "qux");
        assertThat(m1.remove("foo")).isSameAs(m1);
        assertThat(m1).isEqualTo(new Metadata().put("baz", "qux"));
    }

    @Test
    void test_asMap() {
        Metadata metadata = Metadata.from("key", "value");

        Map<String, Object> map = metadata.toMap();

        assertThat(map).containsKey("key").containsValue("value");
    }

    @Test
    void test_create_from_map() {

        Map<String, String> map = singletonMap("key", "value");

        Metadata metadata = Metadata.from(map);

        assertThat(metadata.getString("key")).isEqualTo("value");
    }

    @Test
    void should_create_from_map() {

        // given
        Map<String, Object> map = new HashMap<>();
        map.put("string", "s");

        map.put("integer", 1);
        map.put("integer_as_string", "1");
        map.put("integer_as_long", 1L);
        map.put("integer_as_float", 1f);
        map.put("integer_as_double", 1d);

        map.put("long", 1L);
        map.put("long_as_string", "1");
        map.put("long_as_integer", 1);
        map.put("long_as_float", 1f);
        map.put("long_as_double", 1d);

        map.put("float", 1f);
        map.put("float_as_string", "1");
        map.put("float_as_integer", 1);
        map.put("float_as_long", 1L);
        map.put("float_as_double", 1d);

        map.put("double", 1d);
        map.put("double_as_string", "1");
        map.put("double_as_integer", 1);
        map.put("double_as_long", 1L);
        map.put("double_as_float", 1f);

        UUID uuid = UUID.randomUUID();
        map.put("uuid", uuid);
        map.put("uuid_as_string", uuid.toString());

        // when
        Metadata metadata = new Metadata(map);

        // then
        assertThat(metadata.getString("string")).isEqualTo("s");
        assertThat(metadata.getString("banana")).isNull();
        assertThatThrownBy(() -> metadata.getString("integer"))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("Metadata entry with the key 'integer' has a value of '1' and type 'java.lang.Integer'. " +
                        "It cannot be returned as a String.");

        assertThat(metadata.getUUID("uuid")).isEqualTo(uuid);
        assertThat(metadata.getUUID("uuid_as_string")).isEqualTo(uuid);
        assertThatThrownBy(() -> metadata.getUUID("integer"))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("Metadata entry with the key 'integer' has a value of '1' and type 'java.lang.Integer'. " +
                        "It cannot be returned as a UUID.");

        assertThat(metadata.getInteger("integer")).isEqualTo(1);
        assertThat(metadata.getInteger("integer_as_string")).isEqualTo(1);
        assertThat(metadata.getInteger("integer_as_long")).isEqualTo(1);
        assertThat(metadata.getInteger("integer_as_float")).isEqualTo(1);
        assertThat(metadata.getInteger("integer_as_double")).isEqualTo(1);
        assertThat(metadata.getInteger("banana")).isNull();

        assertThat(metadata.getLong("long")).isEqualTo(1L);
        assertThat(metadata.getLong("long_as_string")).isEqualTo(1L);
        assertThat(metadata.getLong("long_as_integer")).isEqualTo(1L);
        assertThat(metadata.getLong("long_as_float")).isEqualTo(1L);
        assertThat(metadata.getLong("long_as_double")).isEqualTo(1L);
        assertThat(metadata.getLong("banana")).isNull();

        assertThat(metadata.getFloat("float")).isEqualTo(1f);
        assertThat(metadata.getFloat("float_as_string")).isEqualTo(1f);
        assertThat(metadata.getFloat("float_as_integer")).isEqualTo(1f);
        assertThat(metadata.getFloat("float_as_long")).isEqualTo(1f);
        assertThat(metadata.getFloat("float_as_double")).isEqualTo(1f);
        assertThat(metadata.getFloat("banana")).isNull();

        assertThat(metadata.getDouble("double")).isEqualTo(1d);
        assertThat(metadata.getDouble("double_as_string")).isEqualTo(1d);
        assertThat(metadata.getDouble("double_as_integer")).isEqualTo(1d);
        assertThat(metadata.getDouble("double_as_long")).isEqualTo(1d);
        assertThat(metadata.getDouble("double_as_float")).isEqualTo(1d);
        assertThat(metadata.getDouble("banana")).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void should_fail_to_create_from_map_when_key_is_null(String key) {

        // given
        Map<String, Object> map = new HashMap<>();
        map.put(key, "value");

        // when-then
        assertThatThrownBy(() -> new Metadata(map))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The metadata key with the value 'value' cannot be null or blank");

        assertThatThrownBy(() -> Metadata.from(map))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The metadata key with the value 'value' cannot be null or blank");
    }

    @Test
    void should_fail_to_create_from_map_when_value_is_null() {

        // given
        Map<String, Object> map = new HashMap<>();
        map.put("key", null);

        // when-then
        assertThatThrownBy(() -> new Metadata(map))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The metadata value for the key 'key' cannot be null");

        assertThatThrownBy(() -> Metadata.from(map))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The metadata value for the key 'key' cannot be null");
    }

    @Test
    void should_fail_to_create_from_map_when_value_is_of_unsupported_type() {

        // given
        Map<String, Object> map = new HashMap<>();
        map.put("key", new Object());

        // when-then
        assertThatThrownBy(() -> new Metadata(map))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("The metadata key 'key' has the value")
                .hasMessageEndingWith("which is of the unsupported type 'java.lang.Object'. " +
                        "Currently, the supported types are: [class java.lang.String, class java.util.UUID, int, class java.lang.Integer, " +
                        "long, class java.lang.Long, float, class java.lang.Float, double, class java.lang.Double]");

        assertThatThrownBy(() -> Metadata.from(map))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("The metadata key 'key' has the value")
                .hasMessageEndingWith("which is of the unsupported type 'java.lang.Object'. " +
                        "Currently, the supported types are: [class java.lang.String, class java.util.UUID, int, class java.lang.Integer, " +
                        "long, class java.lang.Long, float, class java.lang.Float, double, class java.lang.Double]");
    }

    @Test
    void should_get_typed_values() {
        UUID uuid = UUID.randomUUID();
        Metadata metadata = new Metadata()
                .put("string", "s")
                .put("uuid", uuid)
                .put("integer", 1)
                .put("long", 1L)
                .put("float", 1f)
                .put("double", 1d);

        assertThat(metadata.getString("string")).isEqualTo("s");
        assertThat(metadata.getString("banana")).isNull();

        assertThat(metadata.getUUID("uuid")).isEqualTo(uuid);

        assertThat(metadata.getInteger("integer")).isEqualTo(1);
        assertThat(metadata.getInteger("banana")).isNull();

        assertThat(metadata.getLong("long")).isEqualTo(1L);
        assertThat(metadata.getLong("banana")).isNull();

        assertThat(metadata.getFloat("float")).isEqualTo(1f);
        assertThat(metadata.getFloat("banana")).isNull();

        assertThat(metadata.getDouble("double")).isEqualTo(1d);
        assertThat(metadata.getDouble("banana")).isNull();
    }

    @Test
    void should_fail_when_adding_null_key() {

        // given
        Metadata metadata = new Metadata();

        // when-then
        assertThatThrownBy(() -> metadata.put(null, "value"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The metadata key with the value 'value' cannot be null or blank");

        UUID uuid = UUID.randomUUID();
        assertThatThrownBy(() -> metadata.put(null, uuid))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format("The metadata key with the value '%s' cannot be null or blank", uuid));

        assertThatThrownBy(() -> metadata.put(null, 1))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The metadata key with the value '1' cannot be null or blank");

        assertThatThrownBy(() -> metadata.put(null, 1L))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The metadata key with the value '1' cannot be null or blank");

        assertThatThrownBy(() -> metadata.put(null, 1f))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The metadata key with the value '1.0' cannot be null or blank");

        assertThatThrownBy(() -> metadata.put(null, 1d))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The metadata key with the value '1.0' cannot be null or blank");
    }

    @Test
    void should_fail_when_adding_null_value() {

        // given
        Metadata metadata = new Metadata();

        // when-then
        assertThatThrownBy(() -> metadata.put("key", (String)null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The metadata value for the key 'key' cannot be null");

        assertThatThrownBy(() -> metadata.put("key", (UUID)null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The metadata value for the key 'key' cannot be null");
    }

    @Test
    void should_convert_to_map() {
        UUID uuid = UUID.randomUUID();
        // given
        Map<String, Object> originalMap = new HashMap<>();
        originalMap.put("string", "s");
        originalMap.put("uuid", uuid);
        originalMap.put("integer", 1);
        originalMap.put("long", 1L);
        originalMap.put("float", 1f);
        originalMap.put("double", 1d);
        Metadata metadata = Metadata.from(originalMap);

        // when
        Map<String, Object> map = metadata.toMap();

        // then
        assertThat(map).isEqualTo(originalMap);
    }

    @Test
    void test_containsKey() {
        assertThat(new Metadata().containsKey("key")).isFalse();
        assertThat(new Metadata().put("key", "value").containsKey("key")).isTrue();
    }
}