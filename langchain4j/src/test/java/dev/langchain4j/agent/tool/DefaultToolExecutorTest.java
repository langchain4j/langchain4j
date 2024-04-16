package dev.langchain4j.agent.tool;

import static dev.langchain4j.TestReflectUtil.*;

import dev.langchain4j.internal.Utils;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.*;

class DefaultToolExecutorTest implements WithAssertions {
    @SuppressWarnings("unused")
    public void example(@ToolMemoryId UUID idA, int intP, Double doubleP, List<String> stringsP) {}

    @Test
    public void test_prepareArguments() {
        UUID memoryId = UUID.randomUUID();
        Method method = getMethodByName(getClass(), "example");

        Map<String, Object> argumentsMap =
                Utils.mapOf(
                        entry("arg3", Arrays.asList("hello", "world")),
                        entry("arg1", 1),
                        entry("arg2", 2.0));

        ToolJsonSchemas toolJsonSchemas = new ToolJsonSchemas();
        DefaultToolExecutor executor = new DefaultToolExecutor(this, method, toolJsonSchemas);
        Object[] args = executor.prepareArguments(method, argumentsMap, memoryId);

        // Assert that:
        // - memoryId is inserted at the correct position, and
        // - the order of the arguments is correct.
        assertThat(args).containsExactly(memoryId, 1, 2.0, Arrays.asList("hello", "world"));
    }
}
