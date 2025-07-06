package dev.langchain4j.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JsonParsingUtilsTest {

    static class MyPojo {
        public String name;
        public int age;
        public List<String> tags;
        public Map<String, Object> extra;
        public MyPojo() {}
        public MyPojo(String name, int age) { this.name = name; this.age = age; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MyPojo myPojo = (MyPojo) o;
            return age == myPojo.age &&
                    java.util.Objects.equals(name, myPojo.name);
        }
    }

    @Test
    void extract_simple_object() {
        String json = "{\"name\":\"Tom\",\"age\":18}";
        Optional<JsonParsingUtils.ParsedJson<MyPojo>> result = JsonParsingUtils.extractAndParseJson(json, MyPojo.class);
        assertThat(result).isPresent();
        assertThat(result.get().value().name).isEqualTo("Tom");
        assertThat(result.get().value().age).isEqualTo(18);
    }

    @Test
    void extract_object_with_prefix_suffix() {
        String json = "prefix {\"name\":\"Jerry\",\"age\":20} suffix";
        Optional<JsonParsingUtils.ParsedJson<MyPojo>> result = JsonParsingUtils.extractAndParseJson(json, MyPojo.class);
        assertThat(result).isPresent();
        assertThat(result.get().value().name).isEqualTo("Jerry");
        assertThat(result.get().value().age).isEqualTo(20);
    }

    @Test
    void extract_array() {
        String json = "[{\"name\":\"A\",\"age\":1},{\"name\":\"B\",\"age\":2}]";
        Optional<JsonParsingUtils.ParsedJson<MyPojo[]>> result = JsonParsingUtils.extractAndParseJson(json, MyPojo[].class);
        assertThat(result).isPresent();
        assertThat(result.get().value().length).isEqualTo(2);
        assertThat(result.get().value()[0].name).isEqualTo("A");
        assertThat(result.get().value()[1].age).isEqualTo(2);
    }

    @Test
    void extract_array_with_noise() {
        String json = "abc [{\"name\":\"A\",\"age\":1},{\"name\":\"B\",\"age\":2}] xyz";
        Optional<JsonParsingUtils.ParsedJson<MyPojo[]>> result = JsonParsingUtils.extractAndParseJson(json, MyPojo[].class);
        assertThat(result).isPresent();
        assertThat(result.get().value().length).isEqualTo(2);
    }

    @Test
    void extract_nested_array() {
        String json = "[[{\"name\":\"A\",\"age\":1}],[{\"name\":\"B\",\"age\":2}]]";
        Optional<JsonParsingUtils.ParsedJson<MyPojo[][]>> result = JsonParsingUtils.extractAndParseJson(json, MyPojo[][].class);
        assertThat(result).isPresent();
        assertThat(result.get().value().length).isEqualTo(2);
        assertThat(result.get().value()[0][0].name).isEqualTo("A");
        assertThat(result.get().value()[1][0].name).isEqualTo("B");
    }

    @Test
    void extract_object_with_array_field() {
        String json = "{\"name\":\"Tom\",\"age\":18,\"tags\":[\"a\",\"b\"]}";
        Optional<JsonParsingUtils.ParsedJson<MyPojo>> result = JsonParsingUtils.extractAndParseJson(json, MyPojo.class);
        assertThat(result).isPresent();
        assertThat(result.get().value().tags).containsExactly("a", "b");
    }

    @Test
    void extract_object_with_map_field() {
        String json = "{\"name\":\"Tom\",\"age\":18,\"extra\":{\"k1\":123,\"k2\":\"v2\"}}";
        Optional<JsonParsingUtils.ParsedJson<MyPojo>> result = JsonParsingUtils.extractAndParseJson(json, MyPojo.class);
        assertThat(result).isPresent();
        assertThat(result.get().value().extra).containsEntry("k1", 123).containsEntry("k2", "v2");
    }

    @Test
    void extract_multiple_json_blocks() {
        String json = "foo {\"name\":\"A\",\"age\":1} bar {\"name\":\"B\",\"age\":2}";
        Optional<JsonParsingUtils.ParsedJson<MyPojo>> result = JsonParsingUtils.extractAndParseJson(json, MyPojo.class);
        assertThat(result).isPresent();
        // 应该提取最后一个 JSON
        assertThat(result.get().value().name).isEqualTo("B");
    }

    @Test
    void extract_invalid_json() {
        String json = "not a json";
        Optional<JsonParsingUtils.ParsedJson<MyPojo>> result = JsonParsingUtils.extractAndParseJson(json, MyPojo.class);
        assertThat(result).isEmpty();
    }

    @Test
    void extract_json_with_inner_brackets() {
        String json = "{\"name\":\"Tom\",\"age\":18,\"tags\":[\"a\",\"b\",\"[c]\"]}";
        Optional<JsonParsingUtils.ParsedJson<MyPojo>> result = JsonParsingUtils.extractAndParseJson(json, MyPojo.class);
        assertThat(result).isPresent();
        assertThat(result.get().value().tags).contains("[c]");
    }

    @Test
    void extract_json_with_nested_arrays() {
        String json = "{\"name\":\"Tom\",\"age\":18,\"tags\":[\"a\",[\"b\",\"c\"]]}";
        Optional<JsonParsingUtils.ParsedJson<MyPojo>> result = JsonParsingUtils.extractAndParseJson(json, MyPojo.class);
        assertThat(result).isPresent();
        // tags 字段类型为 List<String>，嵌套数组会被解析为字符串
        assertThat(result.get().value().tags).isNotEmpty();
    }

    @Test
    void extract_json_array_with_nested_objects_and_arrays() {
        String json = "[{\"name\":\"Tom\",\"age\":18,\"tags\":[\"a\",\"b\"]},{\"name\":\"Jerry\",\"age\":20,\"tags\":[\"x\",\"y\"]}]";
        Optional<JsonParsingUtils.ParsedJson<MyPojo[]>> result = JsonParsingUtils.extractAndParseJson(json, MyPojo[].class);
        assertThat(result).isPresent();
        assertThat(result.get().value()[1].tags).containsExactly("x", "y");
    }
} 