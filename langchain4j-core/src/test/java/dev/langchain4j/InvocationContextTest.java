package dev.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InvocationContextTest {

    @Test
    void test1() {
        InvocationContext context = new InvocationContext();
        context.put("key", "value");
        assertThat((Object) context.get("key")).isEqualTo("value"); // TODO casting
    }

    @Test
    void test2() {
        InvocationContext context = InvocationContext.from("key", "value");
        assertThat(context.asMap()).containsOnly(Map.entry("key", "value"));
    }

    @Test
    void test3() {
        InvocationContext context = InvocationContext.from(Map.of("key", "value"));
        assertThat(context.asMap()).containsOnly(Map.entry("key", "value"));
    }

    @Test
    void test4() {
        Map<String, Object> seedMap = new HashMap<>();
        seedMap.put("key", "value");

        InvocationContext context = InvocationContext.from(seedMap);
        assertThat(context.asMap()).containsOnly(Map.entry("key", "value"));

        seedMap.put("key2", "value2");
        context.put("key3", "value3");

        assertThat(seedMap).containsOnly(Map.entry("key", "value"), Map.entry("key2", "value2"));
        assertThat(context.asMap()).containsOnly(Map.entry("key", "value"), Map.entry("key3", "value3"));
    }
}
