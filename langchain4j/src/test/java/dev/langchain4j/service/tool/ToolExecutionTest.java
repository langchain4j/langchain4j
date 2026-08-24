package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolExecutionTest {

    @Test
    void test_deprecated_result_setter() {

        String textResult = "text result";

        InvocationContext invocationContext = InvocationContext.builder()
                .interfaceName("SomeInterface")
                .methodName("someMethod")
                .methodArgument("one")
                .methodArgument("two")
                .chatMemoryId("one")
                .build();

        ToolExecution toolExecution = ToolExecution.builder()
                .request(ToolExecutionRequest.builder().build())
                .result(textResult)
                .invocationContext(invocationContext)
                .build();

        assertThat(toolExecution.result()).isEqualTo(textResult);
        assertThat(toolExecution.resultObject()).isNull();
        assertThat(toolExecution.invocationContext()).isSameAs(invocationContext);
    }

    @Test
    void should_return_attributes_of_the_result() {

        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultText("text result")
                .attributes(Map.of("key", "value"))
                .build();

        ToolExecution toolExecution = ToolExecution.builder()
                .request(ToolExecutionRequest.builder().build())
                .result(result)
                .invocationContext(InvocationContext.builder()
                        .interfaceName("SomeInterface")
                        .methodName("someMethod")
                        .build())
                .build();

        assertThat(toolExecution.attributes()).containsExactly(Map.entry("key", "value"));
    }
}
