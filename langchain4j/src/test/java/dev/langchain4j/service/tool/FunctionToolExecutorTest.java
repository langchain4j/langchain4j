package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.internal.Json;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import java.util.Map;

class FunctionToolExecutorTest implements WithAssertions {

    public static class Arguments {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }

    @Test
    void should_execute_tools_with_function_string() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("1")
                .name("test")
                .arguments("{\"name\":\"function\"}")
                .build();

        String result = FunctionToolExecutor.from(input -> {
            Map<String, Object> param = ToolExecutionRequestUtil.argumentsAsMap(input);
            return param.get("name").toString();
        }).execute(request, "DEFAULT");

        assertThat(result).isEqualTo("function");
    }

    @Test
    void should_execute_tools_with_function_class() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("1")
                .name("test")
                .arguments("{\"name\":\"function\"}")
                .build();

        String result = FunctionToolExecutor.from(input -> {
            // ignore
            return input.getName();
        }, Arguments.class).execute(request, "DEFAULT");

        assertThat(result).isEqualTo("function");
    }

    @Test
    void should_execute_tools_with_consumer_class() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("1")
                .name("test")
                .arguments("{\"name\":\"function\"}")
                .build();

        String result = FunctionToolExecutor.from(input -> {
            // ignore
        }, Arguments.class).execute(request, "DEFAULT");

        assertThat(result).isEqualTo("Success");
    }

}
