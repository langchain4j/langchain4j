package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME;

import com.openai.models.ChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class OpenAiOfficialChatModelIT extends AbstractChatModelIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return InternalOpenAiOfficialTestHelper.chatModelsNormalAndJsonStrict();
    }

    @Override
    protected ChatLanguageModel createModelWith(ChatRequestParameters parameters) {
        OpenAiOfficialChatModel.Builder openAiChatModelBuilder = OpenAiOfficialChatModel.builder()
                .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                .modelName(CHAT_MODEL_NAME)
                .maxCompletionTokens(parameters.maxOutputTokens())
                .defaultRequestParameters(parameters);

        return openAiChatModelBuilder.build();
    }

    @Override
    protected String customModelName() {
        return ChatModel.GPT_4O_2024_08_06.toString();
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OpenAiOfficialChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected boolean supportsModelNameParameter() {
        // With Azure OpenAI, the deployment name is part of the URL, changing the model name will not have any effect.
        return false;
    }
}
