package dev.langchain4j.internal;

import com.google.gson.JsonSyntaxException;
import org.assertj.core.api.WithAssertions;
import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;

class GsonJsonCodecTest implements WithAssertions {
    public static class Example {
        public String name;
        public int age;

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

    private static String readAllBytes(InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[1024];
        while (true) {
            int n = stream.read(buf);
            if (n < 0) {
                break;
            }
            sb.append(new String(buf, 0, n));
        }
        return sb.toString();
    }


    @Test
    public void test() throws Exception {
        GsonJsonCodec codec = new GsonJsonCodec();
        Example example = new Example("John", 42);
        assertThat(codec.fromJson(codec.toJson(example), Example.class)).isEqualTo(example);

        InputStream inputStream = codec.toInputStream(example, Example.class);

        assertThat(codec.fromJson(readAllBytes(inputStream), Example.class)).isEqualTo(example);
    }

    @Test
    public void test_map() {
        GsonJsonCodec codec = new GsonJsonCodec();
        {
            Map<Object, Object> expectedMap = new HashMap<>();
            expectedMap.put("a", "b");

            assertThat(codec.toJson(expectedMap))
                    .isEqualTo("{\n  \"a\": \"b\"\n}");

            assertThat(codec.fromJson("{\"a\": \"b\"}", (Class<?>) expectedMap.getClass()))
                    .isEqualTo(expectedMap);
        }
        {
            Map<Object, Object> map = codec.fromJson("{\"a\": [1, 2]}", Map.class);

            assertThat(map).containsExactly(MapEntry.entry("a", asList(1.0, 2.0)));
        }
    }

    public static class DateExample {
        public LocalDate localDate;
        public LocalDateTime localDateTime;

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

    @Test
    public void test_datetime() {
        GsonJsonCodec codec = new GsonJsonCodec();
        DateExample example = new DateExample(
                LocalDate.of(2019, 1, 1),
                LocalDateTime.of(2019, 1, 1, 0, 0, 0)
        );


        assertThat(codec.toJson(example)).isEqualTo(
                "{\n  \"localDate\": \"2019-01-01\",\n  \"localDateTime\": \"2019-01-01T00:00:00\"\n}");

        assertThat(codec.fromJson(codec.toJson(example), DateExample.class)).isEqualTo(example);
    }

    @Test
    public void test_broken() {
        GsonJsonCodec codec = new GsonJsonCodec();
        assertThatExceptionOfType(JsonSyntaxException.class)
                .isThrownBy(() -> codec.fromJson("abc", Integer.class));

        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> {
                    try (InputStream ignored = codec.toInputStream("abc", Integer.class)) {
                        fail("should not reach here");
                    }
                });
    }
}