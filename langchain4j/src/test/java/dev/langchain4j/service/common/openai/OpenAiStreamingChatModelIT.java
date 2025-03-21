package dev.langchain4j.service.common.openai;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.common.StreamingChatModelAndCapabilities;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.util.List;

// TODO move to langchain4j-open-ai module once dependency cycle is resolved
class OpenAiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    public static OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder defaultStreamingModelBuilder() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI);
    }

    @Override
    protected List<AbstractChatModelAndCapabilities<StreamingChatLanguageModel>> models() {
        return List.of(
                StreamingChatModelAndCapabilities.builder()
                        .model(defaultStreamingModelBuilder().build())
                        .mnemonicName("default openAi chat model")
                        .build(),
                StreamingChatModelAndCapabilities.builder()
                        .model(defaultStreamingModelBuilder().strictTools(true).build())
                        .mnemonicName("openAi chat model with strict tools")
                        .build(),
                StreamingChatModelAndCapabilities.builder()
                        .model(defaultStreamingModelBuilder()
                                .responseFormat("json_schema")
                                .strictJsonSchema(true)
                                .build())
                        .mnemonicName("openAi chat model with json schema response format")
                        .build()
                // TODO json_object?
                );
    }

    @Override
    protected AbstractChatModelAndCapabilities<StreamingChatLanguageModel> createModelAndCapabilitiesWith(
            ChatRequestParameters parameters) {
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder openAiStreamingChatModelBuilder =
                OpenAiStreamingChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .defaultRequestParameters(parameters)
                        .logRequests(true)
                        .logResponses(true);
        if (parameters.modelName() == null) {
            openAiStreamingChatModelBuilder.modelName(GPT_4_O_MINI);
        }

        return StreamingChatModelAndCapabilities.builder()
                .model(openAiStreamingChatModelBuilder.build())
                .build();
    }

    @Override
    protected String customModelName() {
        return "gpt-4o-2024-11-20";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OpenAiChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    // TODO OpenAI-specific tests
}
