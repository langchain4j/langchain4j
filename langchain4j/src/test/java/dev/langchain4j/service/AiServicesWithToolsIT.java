package dev.langchain4j.service;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.description;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.*;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_3_5_TURBO_0613;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AiServicesWithToolsIT {

    @Spy
    ChatLanguageModel chatLanguageModel = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    ToolSpecification expectedCalculatorSpecification = ToolSpecification.builder()
            .name("squareRoot")
            .description("calculates the square root of the provided number")
            .addParameter("arg0", NUMBER, description("number to operate on"))
            .build();

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractions(chatLanguageModel);
    }

    interface Assistant {

        Response<AiMessage> chat(String userMessage);
    }

    static class Calculator {

        @Tool("calculates the square root of the provided number")
        double squareRoot(@P("number to operate on") double number) {
            return Math.sqrt(number);
        }
    }

    @Test
    void should_execute_a_tool_then_answer() {

        Calculator calculator = spy(new Calculator());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .tools(calculator)
                .build();

        String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

        Response<AiMessage> response = assistant.chat(userMessage);

        assertThat(response.content().text()).contains("6.97");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(72 + 109);
        assertThat(tokenUsage.outputTokenCount()).isCloseTo(20 + 31, withPercentage(5));
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verifyNoMoreInteractions(calculator);


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(4);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(messages.get(0).text()).isEqualTo(userMessage);

        AiMessage aiMessage = (AiMessage) messages.get(1);
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotBlank();
        assertThat(toolExecutionRequest.name()).isEqualTo("squareRoot");
        assertThat(toolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": 485906798473894056}");

        ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(2);
        assertThat(toolExecutionResultMessage.id()).isEqualTo(toolExecutionRequest.id());
        assertThat(toolExecutionResultMessage.toolName()).isEqualTo("squareRoot");
        assertThat(toolExecutionResultMessage.text()).isEqualTo("6.97070153193991E8");

        assertThat(messages.get(3)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(3).text()).contains("6.97");


        verify(chatLanguageModel).generate(
                singletonList(messages.get(0)),
                singletonList(expectedCalculatorSpecification)
        );

        verify(chatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2)),
                singletonList(expectedCalculatorSpecification)
        );
    }

    @Test
    void should_execute_multiple_tools_sequentially_then_answer() {

        ChatLanguageModel chatLanguageModel = spy(OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_3_5_TURBO_0613) // this model can only call tools sequentially
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build());

        Calculator calculator = spy(new Calculator());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .tools(calculator)
                .build();

        String userMessage = "What is the square root of 485906798473894056 and 97866249624785 in scientific notation?";

        Response<AiMessage> response = assistant.chat(userMessage);

        assertThat(response.content().text()).contains("6.97", "9.89");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(79 + 117 + 152);
        assertThat(tokenUsage.outputTokenCount()).isCloseTo(21 + 20 + 53, withPercentage(5));
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verify(calculator).squareRoot(97866249624785.0);
        verifyNoMoreInteractions(calculator);


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(6);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(messages.get(0).text()).isEqualTo(userMessage);

        AiMessage aiMessage = (AiMessage) messages.get(1);
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotBlank();
        assertThat(toolExecutionRequest.name()).isEqualTo("squareRoot");
        assertThat(toolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": 485906798473894056}");

        ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(2);
        assertThat(toolExecutionResultMessage.id()).isEqualTo(toolExecutionRequest.id());
        assertThat(toolExecutionResultMessage.toolName()).isEqualTo("squareRoot");
        assertThat(toolExecutionResultMessage.text()).isEqualTo("6.97070153193991E8");

        AiMessage secondAiMessage = (AiMessage) messages.get(3);
        assertThat(secondAiMessage.text()).isNull();
        assertThat(secondAiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest secondToolExecutionRequest = secondAiMessage.toolExecutionRequests().get(0);
        assertThat(secondToolExecutionRequest.id()).isNotBlank();
        assertThat(secondToolExecutionRequest.name()).isEqualTo("squareRoot");
        assertThat(secondToolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": 97866249624785}");

        ToolExecutionResultMessage secondToolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(4);
        assertThat(secondToolExecutionResultMessage.id()).isEqualTo(secondToolExecutionRequest.id());
        assertThat(secondToolExecutionResultMessage.toolName()).isEqualTo("squareRoot");
        assertThat(secondToolExecutionResultMessage.text()).isEqualTo("9892737.215997653");

        assertThat(messages.get(5)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(5).text()).contains("6.97", "9.89");


        verify(chatLanguageModel).generate(
                singletonList(messages.get(0)),
                singletonList(expectedCalculatorSpecification)
        );

        verify(chatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2)),
                singletonList(expectedCalculatorSpecification)
        );

        verify(chatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2), messages.get(3), messages.get(4)),
                singletonList(expectedCalculatorSpecification)
        );
    }

    @Test
    void should_execute_multiple_tools_in_parallel_then_answer() {

        Calculator calculator = spy(new Calculator());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .tools(calculator)
                .build();

        String userMessage = "What is the square root of 485906798473894056 and 97866249624785 in scientific notation?";

        Response<AiMessage> response = assistant.chat(userMessage);

        assertThat(response.content().text()).contains("6.97", "9.89");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(79 + 160);
        assertThat(tokenUsage.outputTokenCount()).isCloseTo(54 + 58, withPercentage(5));
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verify(calculator).squareRoot(97866249624785.0);
        verifyNoMoreInteractions(calculator);


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(5);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(messages.get(0).text()).isEqualTo(userMessage);

        AiMessage aiMessage = (AiMessage) messages.get(1);
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(2);

        ToolExecutionRequest firstToolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(firstToolExecutionRequest.id()).isNotBlank();
        assertThat(firstToolExecutionRequest.name()).isEqualTo("squareRoot");
        assertThat(firstToolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": 485906798473894056}");

        ToolExecutionRequest secondToolExecutionRequest = aiMessage.toolExecutionRequests().get(1);
        assertThat(secondToolExecutionRequest.id()).isNotBlank();
        assertThat(secondToolExecutionRequest.name()).isEqualTo("squareRoot");
        assertThat(secondToolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": 97866249624785}");

        ToolExecutionResultMessage firstToolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(2);
        assertThat(firstToolExecutionResultMessage.id()).isEqualTo(firstToolExecutionRequest.id());
        assertThat(firstToolExecutionResultMessage.toolName()).isEqualTo("squareRoot");
        assertThat(firstToolExecutionResultMessage.text()).isEqualTo("6.97070153193991E8");

        ToolExecutionResultMessage secondToolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(3);
        assertThat(secondToolExecutionResultMessage.id()).isEqualTo(secondToolExecutionRequest.id());
        assertThat(secondToolExecutionResultMessage.toolName()).isEqualTo("squareRoot");
        assertThat(secondToolExecutionResultMessage.text()).isEqualTo("9892737.215997653");

        assertThat(messages.get(4)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(4).text()).contains("6.97", "9.89");


        verify(chatLanguageModel).generate(
                singletonList(messages.get(0)),
                singletonList(expectedCalculatorSpecification)
        );

        verify(chatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2), messages.get(3)),
                singletonList(expectedCalculatorSpecification)
        );
    }

    static class IdsProcessor {

        @Tool("processes list of uuids into result string")
        String processUuids(@P("uuids to use") List<String> uuids) {
            return String.join("_", uuids);
        }
    }

    @Test
    void should_use_tool_with_array_parameters_then_answer() {

        ToolSpecification expectedToolSpecification = ToolSpecification.builder()
                .name("processUuids")
                .description("processes list of uuids into result string")
                .addParameter("arg0", ARRAY, items(STRING), description("uuids to use"))
                .build();

        IdsProcessor listProcessor = spy(new IdsProcessor());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .tools(listProcessor)
                .build();

        String userMessage = "Process uuids 73b7323b-ed32-4782-a91e-3056455b9d75 " +
                "and 8585784a-d3f7-4442-936d-18fa7193ddc2 and provide result of processing";

        Response<AiMessage> response = assistant.chat(userMessage);

        assertThat(response.content().text())
                .contains("73b7323b-ed32-4782-a91e-3056455b9d75_8585784a-d3f7-4442-936d-18fa7193ddc2");

        verify(listProcessor).processUuids(asList(
                "73b7323b-ed32-4782-a91e-3056455b9d75",
                "8585784a-d3f7-4442-936d-18fa7193ddc2"
        ));
        verifyNoMoreInteractions(listProcessor);

        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(4);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(messages.get(0).text()).isEqualTo(userMessage);

        AiMessage aiMessage = (AiMessage) messages.get(1);
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotBlank();
        assertThat(toolExecutionRequest.name()).isEqualTo("processUuids");
        assertThat(toolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\":  [\"73b7323b-ed32-4782-a91e-3056455b9d75\", " +
                        "\"8585784a-d3f7-4442-936d-18fa7193ddc2\"]}");

        ToolExecutionResultMessage firstToolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(2);
        assertThat(firstToolExecutionResultMessage.id()).isEqualTo(toolExecutionRequest.id());
        assertThat(firstToolExecutionResultMessage.toolName()).isEqualTo("processUuids");
        assertThat(firstToolExecutionResultMessage.text())
                .isEqualTo("73b7323b-ed32-4782-a91e-3056455b9d75_8585784a-d3f7-4442-936d-18fa7193ddc2");

        assertThat(messages.get(3)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(3).text())
                .contains("73b7323b-ed32-4782-a91e-3056455b9d75_8585784a-d3f7-4442-936d-18fa7193ddc2");


        verify(chatLanguageModel).generate(
                singletonList(messages.get(0)),
                singletonList(expectedToolSpecification)
        );

        verify(chatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2)),
                singletonList(expectedToolSpecification)
        );
        verifyNoMoreInteractions(chatLanguageModel);
    }
}
