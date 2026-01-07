package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

/**
 * Integration test to verify lazy calculation optimization.
 *
 * This verifies that:
 * 1. JSON marshalling only happens when resultText() is accessed
 * 2. For IMMEDIATE tools, JSON marshalling is completely avoided
 * 3. The optimization provides measurable performance improvement
 */
@Execution(SAME_THREAD)
class LazyCalculationIntegrationTest {

    static class LargeResultObject {
        List<String> data = new ArrayList<>();

        LargeResultObject() {
            // Create a large object to make JSON marshalling noticeable
            for (int i = 0; i < 1000; i++) {
                data.add("Item " + i + " - " + "x".repeat(100));
            }
        }
    }

    static class DemoTool {

        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
        public LargeResultObject immediateTool() {
            return new LargeResultObject();
        }

        @Tool(returnBehavior = ReturnBehavior.TO_LLM)
        public LargeResultObject normalTool() {
            return new LargeResultObject();
        }
    }

    @Test
    void shouldDemonstrateLazyCalculation() {
        final boolean[] supplierCalled = {false};

        ToolExecutionResult result = ToolExecutionResult.builder()
                .result(new LargeResultObject())
                .resultTextSupplier(() -> {
                    supplierCalled[0] = true;
                    return "{\"message\": \"This would be expensive JSON marshalling\"}";
                })
                .build();

        // Supplier should not be called on creation
        assertThat(supplierCalled[0]).isFalse();

        // Accessing result object should not call supplier
        Object obj = result.result();
        assertThat(supplierCalled[0]).isFalse();

        // Accessing resultText() should call supplier
        String text = result.resultText();
        assertThat(supplierCalled[0]).isTrue();
        assertThat(text).isEqualTo("{\"message\": \"This would be expensive JSON marshalling\"}");

        // Accessing resultText() again should use cached value
        String text2 = result.resultText();
        assertThat(text2).isEqualTo(text);
    }

    @Test
    void shouldOptimizeImmediateToolExecution() throws Exception {
        DemoTool tool = new DemoTool();

        // Create executor for immediate tool
        DefaultToolExecutor executor = new DefaultToolExecutor(tool, DemoTool.class.getMethod("immediateTool"));

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("immediateTool")
                .arguments("{}")
                .build();

        ToolExecutionResult result =
                executor.executeWithContext(request, InvocationContext.builder().build());

        // Result should be available
        Object resultObject = result.result();
        assertThat(resultObject).isInstanceOf(LargeResultObject.class);

        // For IMMEDIATE tools, resultText() can still be accessed if needed
        String resultText = result.resultText();
        assertThat(resultText).isNotNull();
    }

    @Test
    void shouldExecuteNormalToolCorrectly() throws Exception {
        DemoTool tool = new DemoTool();

        DefaultToolExecutor executor = new DefaultToolExecutor(tool, DemoTool.class.getMethod("normalTool"));

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("normalTool")
                .arguments("{}")
                .build();

        ToolExecutionResult result =
                executor.executeWithContext(request, InvocationContext.builder().build());

        // Result should be available
        assertThat(result.result()).isInstanceOf(LargeResultObject.class);

        // ResultText should be available for normal tools
        String resultText = result.resultText();
        assertThat(resultText).isNotNull();
        assertThat(resultText.length()).isGreaterThan(0);
    }

    @Test
    void shouldShowPerformanceImprovement() throws Exception {
        DemoTool tool = new DemoTool();

        DefaultToolExecutor executor = new DefaultToolExecutor(tool, DemoTool.class.getMethod("immediateTool"));

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("immediateTool")
                .arguments("{}")
                .build();

        int iterations = 10000;

        // Warm up - increased to ensure JIT compilation
        for (int i = 0; i < 100; i++) {
            ToolExecutionResult warmupResult = executor.executeWithContext(
                    request, InvocationContext.builder().build());
            if (i % 2 == 0) {
                warmupResult.result();
            } else {
                warmupResult.resultText();
            }
        }

        // Test 1: WITHOUT accessing resultText (IMMEDIATE tool scenario)
        long start1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            ToolExecutionResult result = executor.executeWithContext(
                    request, InvocationContext.builder().build());
            // Only access result(), NOT resultText() - no JSON marshalling
            result.result();
        }
        long time1 = System.nanoTime() - start1;

        // Test 2: WITH accessing resultText (normal tool scenario)
        long start2 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            ToolExecutionResult result = executor.executeWithContext(
                    request, InvocationContext.builder().build());
            // Access resultText() - triggers JSON marshalling
            result.resultText();
        }
        long time2 = System.nanoTime() - start2;

        // Verify that avoiding JSON marshalling is faster
        assertThat(time1).isLessThan(time2);

        // Calculate improvement
        double improvement = ((double) (time2 - time1) / time2) * 100;

        // Performance improvement should be noticeable (at least 10%)
        assertThat(improvement).isGreaterThan(10.0);
    }
}
