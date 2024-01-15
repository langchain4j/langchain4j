package dev.langchain4j.model;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.ChatMessage;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;

@SuppressWarnings("deprecation")
class TokenizerTest implements WithAssertions {
    public static class ExampleTokenizer implements Tokenizer {
        @Override
        public int estimateTokenCountInText(String text) {
            return text.split(" ").length;
        }

        @Override
        public int estimateTokenCountInMessage(ChatMessage message) {
            return estimateTokenCountInText(message.text());
        }

        @Override
        public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
            int tokenCount = 0;
            for (ChatMessage message : messages) {
                tokenCount += estimateTokenCountInMessage(message);
            }
            return tokenCount;
        }

        @Override
        public int estimateTokenCountInToolSpecifications(Iterable<ToolSpecification> toolSpecifications) {
            int tokenCount = 0;
            for (ToolSpecification specification : toolSpecifications) {
                tokenCount += estimateTokenCountInText(specification.description());
            }
            return tokenCount;
        }

        @Override
        public int estimateTokenCountInToolExecutionRequests(Iterable<ToolExecutionRequest> toolExecutionRequests) {
            int tokenCount = 0;
            for (ToolExecutionRequest request : toolExecutionRequests) {
                tokenCount += estimateTokenCountInText(request.arguments());
            }
            return tokenCount;
        }
    }

    @SuppressWarnings("unused")
    public static class ExampleTools {
        @Tool(name = "launch", value = "Launch the rockets")
        public void launchRockets() {}

        @Tool(name = "abort", value = "Abort the rockets")
        public void abortRockets() {}
    }

    @Test
    public void test_estimateTokenCountInTools() throws Exception {
        ExampleTokenizer tokenizer = new ExampleTokenizer();

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