package dev.langchain4j.service.openai.common;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.util.List;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;

// TODO move to langchain4j-open-ai module once dependency cycle is resolved
class OpenAiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    public static final OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder OPEN_AI_STREAMING_CHAT_MODEL_BUILDER =
            OpenAiStreamingChatModel.builder()
                    .baseUrl(System.getenv("OPENAI_BASE_URL"))
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                    .modelName(GPT_4_O_MINI);

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                OPEN_AI_STREAMING_CHAT_MODEL_BUILDER
                        .build(),
                OPEN_AI_STREAMING_CHAT_MODEL_BUILDER
                        .strictTools(true)
                        .build()
                // TODO json_object? json_schema?
        );
    }
}
