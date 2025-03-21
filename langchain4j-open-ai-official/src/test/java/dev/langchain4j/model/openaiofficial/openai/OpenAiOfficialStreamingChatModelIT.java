package dev.langchain4j.model.openaiofficial.openai;

import static dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities.SupportStatus.NOT_SUPPORTED;
import static dev.langchain4j.model.openaiofficial.azureopenai.InternalAzureOpenAiOfficialTestHelper.CHAT_MODEL_NAME_ALTERNATE;

import com.openai.models.ChatModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.common.StreamingChatModelAndCapabilities;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialStreamingChatModel;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialStreamingChatModelIT extends AbstractStreamingChatModelIT {

    @Override
    protected List<AbstractChatModelAndCapabilities<StreamingChatLanguageModel>> models() {
        return InternalOpenAiOfficialTestHelper.chatModelsStreamingNormalAndJsonStrict();
    }

    @Override
    protected AbstractChatModelAndCapabilities<StreamingChatLanguageModel> createModelAndCapabilitiesWith(
            ChatRequestParameters parameters) {
        OpenAiOfficialStreamingChatModel.Builder openAiChatModelBuilder = OpenAiOfficialStreamingChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .defaultRequestParameters(parameters);

        if (parameters.modelName() == null) {
            openAiChatModelBuilder.modelName(CHAT_MODEL_NAME_ALTERNATE);
        }
        return StreamingChatModelAndCapabilities.builder()
                .model(openAiChatModelBuilder.build())
                .mnemonicName("OPENAI_DEFAULT_MODEL")
                .supportsModelNameParameter(NOT_SUPPORTED)
                .supportsToolsAndJsonResponseFormatWithSchema(NOT_SUPPORTED)
                .supportsSingleImageInputAsBase64EncodedString(NOT_SUPPORTED)
                .build();
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
