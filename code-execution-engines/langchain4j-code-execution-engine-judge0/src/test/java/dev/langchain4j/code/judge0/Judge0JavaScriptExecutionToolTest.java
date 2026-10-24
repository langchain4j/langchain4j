package dev.langchain4j.code.judge0;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationsFrom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.Test;

class Judge0JavaScriptExecutionToolTest {

    @Test
    void should_expose_code_parameter_by_name() {

        ToolSpecification toolSpecification =
                toolSpecificationsFrom(Judge0JavaScriptExecutionTool.class).get(0);

        assertThat(toolSpecification.parameters().properties()).containsOnlyKeys("javaScriptCode");
        assertThat(toolSpecification.parameters().required()).containsExactly("javaScriptCode");
    }
}
