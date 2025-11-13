package dev.langchain4j.invocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

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

    @Test
    void testGetNonExistentKey() {
        InvocationParameters invocationParameters = new InvocationParameters();
        invocationParameters.put("existing", "value");

        Object result = invocationParameters.get("nonExistent");

        assertThat(result).isNull();
    }

    @Test
    void testOverwriteExistingValue() {
        InvocationParameters invocationParameters = new InvocationParameters();
        invocationParameters.put("key", "originalValue");
        invocationParameters.put("key", "newValue");

        String value = invocationParameters.get("key");

        assertThat(value).isEqualTo("newValue");
    }

    @Test
    void testMultiplePuts() {
        InvocationParameters invocationParameters = new InvocationParameters();
        invocationParameters.put("key1", "value1");
        invocationParameters.put("key2", "value2");
        invocationParameters.put("key3", "value3");

        assertThat(invocationParameters.asMap())
                .containsOnly(Map.entry("key1", "value1"), Map.entry("key2", "value2"), Map.entry("key3", "value3"));
    }

    @Test
    void testFromWithEmptyMap() {
        InvocationParameters invocationParameters = InvocationParameters.from(new HashMap<>());

        assertThat(invocationParameters.asMap()).isEmpty();
    }

    @Test
    void testFromWithMultipleEntries() {
        Map<String, Object> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", 1);
        map.put("key3", true);

        InvocationParameters invocationParameters = InvocationParameters.from(map);

        assertThat(invocationParameters.asMap())
                .containsOnly(Map.entry("key1", "value1"), Map.entry("key2", 1), Map.entry("key3", true));
    }

    @Test
    void testComplexObjectAsValue() {
        InvocationParameters invocationParameters = new InvocationParameters();
        Map<String, String> complexObject = Map.of("nested", "value");
        invocationParameters.put("complex", complexObject);

        @SuppressWarnings("unchecked")
        Map<String, String> retrieved = (Map<String, String>) invocationParameters.get("complex");

        assertThat(retrieved).isEqualTo(complexObject);
    }

    @Test
    void testFromKeyValueWithNullValue() {
        assertThatThrownBy(() -> InvocationParameters.from("key", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testPutNullValue() {
        InvocationParameters invocationParameters = new InvocationParameters();

        assertThatThrownBy(() -> invocationParameters.put("nullKey", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testFromWithNullMap() {
        assertThatThrownBy(() -> InvocationParameters.from((Map<String, Object>) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPutNullKey() {
        InvocationParameters invocationParameters = new InvocationParameters();

        assertThatThrownBy(() -> invocationParameters.put(null, "value")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testFromKeyValueWithNullKey() {
        assertThatThrownBy(() -> InvocationParameters.from(null, "value")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testGetWithNullKey() {
        InvocationParameters invocationParameters = new InvocationParameters();
        invocationParameters.put("key", "value");

        assertThatThrownBy(() -> invocationParameters.get(null)).isInstanceOf(NullPointerException.class);
    }
}
