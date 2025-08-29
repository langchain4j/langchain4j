package dev.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExtraParametersTest {

    @Test
    void test1() {
        ExtraParameters extraParameters = new ExtraParameters();
        extraParameters.put("key", "value");
        assertThat((Object) extraParameters.get("key")).isEqualTo("value"); // TODO casting
    }

    @Test
    void test2() {
        ExtraParameters extraParameters = ExtraParameters.from("key", "value");
        assertThat(extraParameters.asMap()).containsOnly(Map.entry("key", "value"));
    }

    @Test
    void test3() {
        ExtraParameters extraParameters = ExtraParameters.from(Map.of("key", "value"));
        assertThat(extraParameters.asMap()).containsOnly(Map.entry("key", "value"));
    }

    @Test
    void test4() {
        Map<String, Object> seedMap = new HashMap<>();
        seedMap.put("key", "value");

        ExtraParameters extraParameters = ExtraParameters.from(seedMap);
        assertThat(extraParameters.asMap()).containsOnly(Map.entry("key", "value"));

        seedMap.put("key2", "value2");
        extraParameters.put("key3", "value3");

        assertThat(seedMap).containsOnly(Map.entry("key", "value"), Map.entry("key2", "value2"));
        assertThat(extraParameters.asMap()).containsOnly(Map.entry("key", "value"), Map.entry("key3", "value3"));
    }
}
