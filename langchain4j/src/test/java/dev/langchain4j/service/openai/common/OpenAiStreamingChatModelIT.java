package dev.langchain4j.service.openai.common;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.request.ChatParameters;
import dev.langchain4j.model.openai.OpenAiChatParameters;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.util.List;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;

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
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                defaultStreamingModelBuilder()
                        .build(),
                defaultStreamingModelBuilder()
                        .strictTools(true)
                        .build(),
                defaultStreamingModelBuilder()
                        .responseFormat("json_schema")
                        .strictJsonSchema(true)
                        .build()
                // TODO json_object?
        );
    }

    @Override
    protected StreamingChatLanguageModel createModelWith(ChatParameters chatParameters) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .parameters(chatParameters)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    protected String customModelName() {
        return "gpt-4o-2024-11-20";
    }

    @Override
    protected ChatParameters createIntegrationSpecificChatParameters(int maxOutputTokens) {
        return OpenAiChatParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    // TODO OpenAI-specific tests
}
