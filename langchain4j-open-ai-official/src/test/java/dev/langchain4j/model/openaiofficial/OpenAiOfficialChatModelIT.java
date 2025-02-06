package dev.langchain4j.model.openaiofficial;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatModel;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
public class OpenAiOfficialChatModelIT {

    public static final ChatModel MODEL_NAME = ChatModel.GPT_4O_MINI;
    public static final AzureOpenAIServiceVersion API_VERSION = AzureOpenAIServiceVersion.fromString("2024-06-01");

    OpenAiOfficialChatModel model = OpenAiOfficialChatModel.builder()
            .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
            .azureDeploymentName(MODEL_NAME.toString())
            .azureOpenAIServiceVersion(API_VERSION)
            .modelName(MODEL_NAME)
            .temperature(0.0)
            .build();

    ToolSpecification calculator = ToolSpecification.builder()
            .name("calculator")
            .description("returns a sum of two numbers")
            .parameters(JsonObjectSchema.builder()
                    .addIntegerProperty("first", "The first number")
                    .addIntegerProperty("second", "The second number")
                    .required("first", "second")
                    .build())
            .build();

    /**
     * This uses the OpenAI SDK directly as in https://github.com/openai/openai-java/blob/main/README.md
     * It is used to check if the underlying API is working correctly, as this module is built on it.
     */
    @Test
    void openai_sdk_direct_usage_should_work_with_azure_openai() {

        String baseUrl = System.getenv("AZURE_OPENAI_ENDPOINT") + "/openai/deployments/" + MODEL_NAME.toString() + "?api-version=" + API_VERSION.value();

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .credential(AzureApiKeyCredential.create(System.getenv("AZURE_OPENAI_KEY")))
                .build();

        ChatCompletionCreateParams createParams = ChatCompletionCreateParams.builder()
                .model(MODEL_NAME)
                .maxCompletionTokens(2048)
                .addUserMessage("What is the capital of France?")
                .build();

        client.chat().completions().create(createParams).choices().stream()
                .flatMap(choice -> choice.message().content().stream())
                .forEach(System.out::println);
    }

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {

        // given
        UserMessage userMessage = userMessage("What is the capital of France?");

        // when
        Response<AiMessage> response = model.generate(userMessage);

        // then
        assertThat(response.content().text()).contains("Paris");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(14);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_respect_maxCompletionTokens() {

        // given
        int maxCompletionTokens = 1;

        ChatLanguageModel model = OpenAiOfficialChatModel.builder()
                .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                .azureDeploymentName(MODEL_NAME.toString())
                .azureOpenAIServiceVersion(API_VERSION)
                .modelName(MODEL_NAME)
                .maxCompletionTokens(maxCompletionTokens)
                .temperature(0.0)
                .build();

        UserMessage userMessage = userMessage("Tell me a long story");

        // when
        Response<AiMessage> response = model.generate(userMessage);

        // then
        assertThat(response.content().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(maxCompletionTokens);

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    @Test
    void should_execute_a_tool_then_answer() {

        // given
        UserMessage userMessage = userMessage("2+2=?");
        List<ToolSpecification> toolSpecifications = singletonList(calculator);

        // when
        Response<AiMessage> response = model.generate(singletonList(userMessage), toolSpecifications);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotBlank();
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        Response<AiMessage> secondResponse = model.generate(messages);

        // then
        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.inputTokenCount()).isEqualTo(37);
        assertThat(secondTokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_execute_multiple_tools_in_parallel_then_answer() {

        UserMessage userMessage = userMessage("2+2=? 3+3=?");
        List<ToolSpecification> toolSpecifications = singletonList(calculator);

        // when
        Response<AiMessage> response = model.generate(singletonList(userMessage), toolSpecifications);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(2);

        ToolExecutionRequest toolExecutionRequest1 =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest1.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        ToolExecutionRequest toolExecutionRequest2 =
                aiMessage.toolExecutionRequests().get(1);
        assertThat(toolExecutionRequest2.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest2.arguments()).isEqualToIgnoringWhitespace("{\"first\": 3, \"second\": 3}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage1 = from(toolExecutionRequest1, "4");
        ToolExecutionResultMessage toolExecutionResultMessage2 = from(toolExecutionRequest2, "6");

        List<ChatMessage> messages =
                asList(userMessage, aiMessage, toolExecutionResultMessage1, toolExecutionResultMessage2);

        // when
        Response<AiMessage> secondResponse = model.generate(messages);

        // then
        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("4", "6");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    static class Person {

        String name;
        String surname;
    }

    @Test
    void should_generate_valid_json() {

        // given
        String userMessage = "Return JSON with two fields: name and surname of Klaus Heisler. "
                + "Before returning, tell me a joke."; // nudging it to say something additionally to json

        ChatLanguageModel modelGeneratingJson = OpenAiOfficialChatModel.builder()
                .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                .azureDeploymentName(MODEL_NAME.toString())
                .azureOpenAIServiceVersion(API_VERSION)
                .modelName(MODEL_NAME)
                .responseFormat("json_object")
                .temperature(0.0)
                .build();

        // when
        String json = modelGeneratingJson.generate(userMessage);

        // then
        Person person = Json.fromJson(json, Person.class);
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.surname).isEqualTo("Heisler");
    }
}
