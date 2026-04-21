package dev.langchain4j.model.openai.common.responses;

import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiResponsesChatModelIT extends AbstractChatModelIT {

    private static final String GPT_5_4_MINI = "gpt-5.4-mini";
    private static final int MAX_OUTPUT_TOKENS_MIN_VALUE = 16;

    @Override
    protected List<ChatModel> models() {
        return List.of(
                OpenAiResponsesChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_5_4_MINI)
                        .logRequests(false) // images are huge in logs
                        .logResponses(true)
                        .build()
        );
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        return OpenAiResponsesChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .defaultRequestParameters(parameters)
                .modelName(getOrDefault(parameters.modelName(), GPT_5_4_MINI))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    protected String customModelName() {
        return "gpt-4o-2024-11-20";
    }

    @Override
    protected int maxOutputTokens() {
        return MAX_OUTPUT_TOKENS_MIN_VALUE;
    }

    @Override
    protected ChatRequestParameters saveTokens(ChatRequestParameters parameters) {
        return parameters.overrideWith(ChatRequestParameters.builder()
                .maxOutputTokens(MAX_OUTPUT_TOKENS_MIN_VALUE)
                .build());
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OpenAiResponsesChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel model) {
        return OpenAiResponsesChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel model) {
        return OpenAiTokenUsage.class;
    }

    @Override
    protected boolean supportsStopSequencesParameter() {
        return false;
    }

    @Disabled("gpt-5.4-mini cannot do it properly")
    @Override
    protected void should_respect_JsonRawSchema_responseFormat(ChatModel model) {
    }

    @Test
    void should_accept_pdf_file_content_as_public_url() {

        ChatModel model = OpenAiResponsesChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_5_4_MINI)
                .logRequests(false) // PDF is huge in logs
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.builder()
                .addContent(TextContent.from(
                        "What city appears in the attached PDF? Return only the city name."))
                .addContent(PdfFileContent.from(PdfFile.builder()
                        .url("https://orimi.com/pdf-test.pdf")
                        .build()))
                .build();

        ChatResponse response = model.chat(userMessage);

        assertThat(response.aiMessage().text()).containsIgnoringCase("Whitehorse");
    }
}
