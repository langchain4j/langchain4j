package dev.langchain4j.data.document;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singletonMap;

class MetadataTest implements WithAssertions {

    @Test
    public void test_add_get_put() {
        Metadata m = new Metadata();

        assertThat(m.get("foo")).isNull();
        m.add("foo", "bar");
        assertThat(m.get("foo")).isEqualTo("bar");

        m.add("xyz", 2);
        assertThat(m.get("xyz")).isEqualTo("2");
    }

    @Test
    public void test_map_constructor_copies() {
        Map<String, String> source = new HashMap<>();
        source.put("foo", "bar");

        Metadata m = new Metadata(source);
        Map<String, String> sourceCopy = new HashMap<>(source);

        source.put("baz", "qux");
        assertThat(m.asMap()).isEqualTo(sourceCopy);
    }

    @Test
    public void test_toString() {
        Metadata m = new Metadata();
        m.add("foo", "bar");
        m.add("baz", "qux");
        assertThat(m.toString()).isEqualTo("Metadata { metadata = {foo=bar, baz=qux} }");
    }

    @Test
    public void test_equals_hash() {
        Metadata m1 = new Metadata();
        Metadata m2 = new Metadata();
        m1.add("foo", "bar");
        m2.add("foo", "bar");

        Metadata m3 = new Metadata();
        Metadata m4 = new Metadata();
        m3.add("different", "value");
        m4.add("different", "value");

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
        m1.add("foo", "bar");
        Metadata m2 = m1.copy();
        assertThat(m1).isEqualTo(m2);
        m1.add("foo", "baz");
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
                .isEqualTo(new Metadata().add("foo", "bar").add("baz", "qux"));

        assertThat(Metadata.from("foo", "bar"))
                .isEqualTo(new Metadata().add("foo", "bar"));

        assertThat(Metadata.metadata("foo", "bar"))
                .isEqualTo(new Metadata().add("foo", "bar"));

        assertThat(Metadata.from("foo", 2))
                .isEqualTo(new Metadata().add("foo", "2"));
        assertThat(Metadata.metadata("foo", 2))
                .isEqualTo(new Metadata().add("foo", "2"));
    }

    @Test
    public void test_remove() {
        Metadata m1 = new Metadata();
        m1.add("foo", "bar");
        m1.add("baz", "qux");
        assertThat(m1.remove("foo")).isSameAs(m1);
        assertThat(m1).isEqualTo(new Metadata().add("baz", "qux"));
    }

    @Test
    void test_asMap() {
        Metadata metadata = Metadata.from("key", "value");

        Map<String, String> map = metadata.asMap();

        assertThat(map).containsKey("key").containsValue("value");
    }

    @Test
    void test_create_from_map() {

        Map<String, String> map = singletonMap("key", "value");

        Metadata metadata = Metadata.from(map);

        assertThat(metadata.get("key")).isEqualTo("value");
    }

    @Test
    void test_get() {

        Metadata metadata = new Metadata()
                .add("string", "s")
                .add("integer", 1)
                .add("double", 1.0)
                .add("boolean", true);
        // TODO more types

        assertThat(metadata.getString("string")).isEqualTo("s");
        assertThat(metadata.getInteger("integer")).isEqualTo(1);
        assertThat(metadata.getDouble("double")).isEqualTo(1.0);
        assertThat(metadata.getBoolean("boolean")).isEqualTo(true);
        // TODO more types
    }

    // TODO more tests
}