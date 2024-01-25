package dev.langchain4j.model;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;

class TokenizerTest implements WithAssertions {

    @SuppressWarnings("unused")
    public static class ExampleTools {
        @Tool(name = "launch", value = "Launch the rockets")
        public void launchRockets() {}

        @Tool(name = "abort", value = "Abort the rockets")
        public void abortRockets() {}
    }

    @Test
    public void test_estimateTokenCountInTools() throws Exception {
        ExampleTestTokenizer tokenizer = new ExampleTestTokenizer();

        ExampleTools exampleTools = new ExampleTools();

        // (Object)
        assertThat(tokenizer.estimateTokenCountInTools(exampleTools))
                .isEqualTo(6);

        // (Iterable<Object>)
        assertThat(tokenizer.estimateTokenCountInTools(asList(exampleTools, exampleTools)))
                .isEqualTo(12);

        // (ToolExecutionRequest)
        assertThat(tokenizer.estimateTokenCountInForcefulToolExecutionRequest(
                ToolExecutionRequest.builder()
                        .id("id")
                        .name("name")
                        .arguments("foo bar baz")
                        .build()))
                .isEqualTo(3);

        // (ToolSpecification)
        assertThat(tokenizer.estimateTokenCountInForcefulToolSpecification(
                ToolSpecifications.toolSpecificationFrom(ExampleTools.class.getMethod("launchRockets"))))
                .isEqualTo(3);
    }
}