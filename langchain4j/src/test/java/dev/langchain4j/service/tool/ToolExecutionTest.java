package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
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
}
