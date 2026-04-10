package dev.langchain4j.model.openai;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiResponsesChatModelIT {

    private static final String RESPONSES_MODEL = System.getenv().getOrDefault("OPENAI_RESPONSES_MODEL", "gpt-5.4");

    private final OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(RESPONSES_MODEL)
            .reasoningEffort("low")
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_generate_person_using_a_tool() {

        ToolSpecification createPerson = ToolSpecification.builder()
                .name("create_person")
                .description("Creates a person profile")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("theme")
                        .required("theme")
                        .build())
                .build();

        ChatRequest firstRequest = ChatRequest.builder()
                .messages(userMessage("Generate a person for a travel app onboarding. Use the create_person tool."))
                .toolSpecifications(createPerson)
                .toolChoice(REQUIRED)
                .build();

        ChatResponse firstResponse = model.chat(firstRequest);

        assertThat(firstResponse.aiMessage().toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest =
                firstResponse.aiMessage().toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("create_person");

        ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(
                toolExecutionRequest,
                "{\"name\":\"Ava Carter\",\"age\":34,\"occupation\":\"designer\",\"city\":\"Denver\"}");

        ChatRequest secondRequest = ChatRequest.builder()
                .messages(firstRequest.messages().get(0), firstResponse.aiMessage(), toolExecutionResultMessage)
                .build();

        ChatResponse finalResponse = model.chat(secondRequest);

        assertThat(finalResponse.aiMessage().text()).containsIgnoringCase("Ava").containsIgnoringCase("Denver");
    }
}
