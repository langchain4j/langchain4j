package dev.langchain4j.invocation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InvocationParametersTest {

    @Test
    void test1() {
        InvocationParameters invocationParameters = new InvocationParameters();
        invocationParameters.put("key", "value");

        String value = invocationParameters.get("key");

        assertThat((Object) value).isEqualTo("value");
    }

    @Test
    void test2() {
        InvocationParameters invocationParameters = InvocationParameters.from("key", "value");
        assertThat(invocationParameters.asMap()).containsOnly(Map.entry("key", "value"));
    }

    @Test
    void test3() {
        InvocationParameters invocationParameters = InvocationParameters.from(Map.of("key", "value"));
        assertThat(invocationParameters.asMap()).containsOnly(Map.entry("key", "value"));
    }

    @Test
    void test4() {
        Map<String, Object> seedMap = new HashMap<>();
        seedMap.put("key1", "value1");

        InvocationParameters invocationParameters = InvocationParameters.from(seedMap);
        assertThat(invocationParameters.asMap()).containsOnly(Map.entry("key1", "value1"));

        seedMap.put("key2", "value2");
        invocationParameters.put("key3", "value3");

        assertThat(seedMap).containsOnlyKeys("key1", "key2");
        assertThat(invocationParameters.asMap()).containsOnlyKeys("key1", "key3");
    }
}
