package dev.langchain4j.service.common.openai;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;

import java.util.List;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;

// TODO move to langchain4j-open-ai module once dependency cycle is resolved
class OpenAiStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    private static OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder defaultStreamingModelBuilder() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI);
    }

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(
                defaultStreamingModelBuilder().build()
                // TODO more configs?
        );
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType() {
        return OpenAiChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType() {
        return OpenAiTokenUsage.class;
    }
}
