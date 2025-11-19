package dev.langchain4j.service.tool;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

public class HallucinatedToolNameStrategyIT {

    OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .temperature(0.8)
            .logRequests(true)
            .build();

    interface Assistant {

        TokenStream chat(String userMessage);
    }

    @Test
    void should_execute_tool_without_interrupt() throws Exception {

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(model)
                .tools(new Calculator())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                // Use the custom HallucinatedToolNameStrategy let the LLM try to solve hallucination issues itself,
                // instead of throwing a RuntimeException.
                // Due to the uncertainty of the LLM hallucination, it may occur randomly.
                // To trigger hallucinations, you can try some more complex tools.
                .hallucinatedToolNameStrategy(
                        toolExecutionRequest -> ToolExecutionResultMessage.from(
                                toolExecutionRequest,
                                toolExecutionRequest.name()
                                        + "' is not a tool. please check the tool specifications again and use available tools."))
                .build();

        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

        assistant
                .chat(userMessage)
                .onPartialResponse(answerBuilder::append)
                .onCompleteResponse(response -> {
                    futureAnswer.complete(answerBuilder.toString());
                    futureResponse.complete(response);
                })
                .onError(futureAnswer::completeExceptionally)
                .start();

        String answer = futureAnswer.get(120, SECONDS);
        ChatResponse response = futureResponse.get(120, SECONDS);

        assertThat(answer).contains("6.97");
        assertThat(response.aiMessage().text()).contains("6.97");

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    static class Calculator {

        @Tool("calculates the square root of the provided number")
        double squareRoot(@P("number to operate on") double number) {
            return Math.sqrt(number);
        }
    }
}
