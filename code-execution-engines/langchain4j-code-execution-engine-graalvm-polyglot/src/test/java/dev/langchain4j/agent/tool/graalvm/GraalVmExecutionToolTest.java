package dev.langchain4j.agent.tool.graalvm;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationsFrom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GraalVmExecutionToolTest {

    @ParameterizedTest
    @ValueSource(classes = {GraalVmJavaScriptExecutionTool.class, GraalVmPythonExecutionTool.class})
    void should_expose_code_parameter_by_name(Class<?> toolClass) {

        ToolSpecification toolSpecification = toolSpecificationsFrom(toolClass).get(0);

        assertThat(toolSpecification.parameters().properties()).containsOnlyKeys("code");
        assertThat(toolSpecification.parameters().required()).containsExactly("code");
    }
}
