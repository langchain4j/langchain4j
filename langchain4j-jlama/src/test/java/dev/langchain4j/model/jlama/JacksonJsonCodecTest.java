package dev.langchain4j.model.jlama;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.List;


class JacksonJsonCodecTest {

    @Test
    public void test() {
        Example example = new Example("John", 42);
        String json = "{\"name\":\"John\",\"age\":42}";

        assertThat(JacksonJsonCodec.fromJson(json, Example.class)).isEqualTo(example);
    }

    @Test
    public void test_map() {
        Map<String, String> expectedMap = new HashMap<>();
        expectedMap.put("a", "b");

        String json = "{\"a\": \"b\"}";
        Map<String, String> resultMap = JacksonJsonCodec.fromJson(json, Map.class);

        assertThat(resultMap).isEqualTo(expectedMap);

        String complexJson = "{\"a\": [1.0, 2.0]}";
        Map<String, List<Double>> complexMap = JacksonJsonCodec.fromJson(complexJson, Map.class);

        assertThat(complexMap).containsEntry("a", List.of(1.0, 2.0));
    }

    @Test
    public void test_datetime() {
        DateExample example = new DateExample(
                LocalDate.of(2019, 1, 1),
                LocalDateTime.of(2019, 1, 1, 0, 0, 0)
        );

        String json = "{\"localDate\":\"2019-01-01\",\"localDateTime\":\"2019-01-01T00:00:00\"}";

        assertThat(JacksonJsonCodec.fromJson(json, DateExample.class)).isEqualTo(example);
    }

    @Test
    public void test_broken() {
        assertThatExceptionOfType(JacksonProcessingException.class)
                .isThrownBy(() -> JacksonJsonCodec.fromJson("abc", Integer.class))
                .withMessageContaining("Error converting JSON to object");
    }

    public static class Example {
        public String name;
        public int age;

        public Example() {
        }

        public Example(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Example)) return false;
            Example example = (Example) o;
            return age == example.age &&
                    name.equals(example.name);
        }
    }

    public static class DateExample {
        public LocalDate localDate;
        public LocalDateTime localDateTime;

        public DateExample() {
        }

        public DateExample(LocalDate localDate, LocalDateTime localDateTime) {
            this.localDate = localDate;
            this.localDateTime = localDateTime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DateExample)) return false;
            DateExample that = (DateExample) o;
            return localDate.equals(that.localDate) &&
                    localDateTime.equals(that.localDateTime);
        }
    }
}
