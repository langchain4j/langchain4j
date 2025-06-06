package dev.langchain4j.model.openaiofficial.openai;

import static dev.langchain4j.model.openaiofficial.openai.InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME_ALTERNATE;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatRequestParameters;
import java.util.List;

import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatResponseMetadata;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialChatModelIT extends AbstractChatModelIT {

    @Override
    protected List<ChatModel> models() {
        return InternalOpenAiOfficialTestHelper.chatModelsNormalAndJsonStrict();
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        OpenAiOfficialChatModel.Builder openAiChatModelBuilder = OpenAiOfficialChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .defaultRequestParameters(parameters);

        if (parameters.modelName() == null) {
            openAiChatModelBuilder.modelName(CHAT_MODEL_NAME_ALTERNATE);
        }
        return openAiChatModelBuilder.build();
    }

    @Override
    protected String customModelName() {
        return com.openai.models.ChatModel.GPT_4O_2024_11_20.toString();
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OpenAiOfficialChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel chatModel) {
        return OpenAiOfficialChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel chatModel) {
        return OpenAiOfficialTokenUsage.class;
    }
}
