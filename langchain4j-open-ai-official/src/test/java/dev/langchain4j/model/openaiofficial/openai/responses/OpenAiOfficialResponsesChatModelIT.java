package dev.langchain4j.model.openaiofficial.openai.responses;

import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatResponseMetadata;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialResponsesChatModelIT extends AbstractChatModelIT {

    private static final String GPT_5_4_MINI = "gpt-5.4-mini";
    private static final int MAX_OUTPUT_TOKENS_MIN_VALUE = 16;

    @Override
    protected List<ChatModel> models() {
        ChatModel model = OpenAiOfficialResponsesChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(GPT_5_4_MINI)
                .build();

        return List.of(model);
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        return OpenAiOfficialResponsesChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .defaultRequestParameters(parameters)
                .modelName(getOrDefault(parameters.modelName(), "gpt-5.4-mini"))
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
                .maxOutputTokens(MAX_OUTPUT_TOKENS_MIN_VALUE).build());
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OpenAiOfficialChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel model) {
        return OpenAiOfficialResponsesChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel model) {
        return OpenAiOfficialTokenUsage.class;
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

        ChatModel model = OpenAiOfficialResponsesChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(GPT_5_4_MINI)
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
