package dev.langchain4j.model.openaiofficial.openai;

import static dev.langchain4j.model.openaiofficial.azureopenai.InternalAzureOpenAiOfficialTestHelper.CHAT_MODEL_NAME_ALTERNATE;

import com.openai.models.ChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatRequestParameters;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialChatModelIT extends AbstractChatModelIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return InternalOpenAiOfficialTestHelper.chatModelsNormalAndJsonStrict();
    }

    @Override
    protected ChatLanguageModel createModelWith(ChatRequestParameters parameters) {
        OpenAiOfficialChatModel.Builder openAiChatModelBuilder = OpenAiOfficialChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(parameters.modelName())
                .maxCompletionTokens(parameters.maxOutputTokens())
                .defaultRequestParameters(parameters);

        if (parameters.modelName() == null) {
            openAiChatModelBuilder.modelName(CHAT_MODEL_NAME_ALTERNATE);
        }
        return openAiChatModelBuilder.build();
    }

    @Override
    protected String customModelName() {
        return ChatModel.GPT_4O_2024_11_20.toString();
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OpenAiOfficialChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }
}
