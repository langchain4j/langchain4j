package dev.langchain4j.model.oracle.oci.genai;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cohere chat models hosted on OCI GenAI.
 * <p>OCI Generative AI is a fully managed service that provides a set of state-of-the-art,
 * customizable large language models (LLMs) that cover a wide range of use cases for text
 * generation, summarization, and text embeddings.
 *
 * <p>To learn more about the service, see the <a href="https://docs.oracle.com/iaas/Content/generative-ai/home.htm">Generative AI documentation</a>
 */
public class OciGenAiCohereChatModel extends BaseCohereChatModel<OciGenAiCohereChatModel> implements ChatModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(OciGenAiCohereChatModel.class);
    private final Builder builder;

    OciGenAiCohereChatModel(Builder builder) {
        super(builder);
        this.builder = builder;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OCI_GEN_AI;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return this.builder.listeners();
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return ChatRequestParameters.builder()
                .modelName(builder.chatModelId())
                .frequencyPenalty(builder.frequencyPenalty())
                .maxOutputTokens(builder.maxTokens())
                .presencePenalty(builder.presencePenalty())
                .stopSequences(builder.stop())
                .temperature(builder.temperature())
                .topK(builder.topK())
                .topP(builder.topP())
                .build();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        var b = prepareRequest(chatRequest);
        return map(super.ociChat(b.build()), builder.chatModelId());
    }

    /**
     * Create a new builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Model builder.
     */
    public static class Builder extends BaseCohereChatModel.Builder<OciGenAiCohereChatModel, Builder> {
        Builder() {}

        @Override
        Builder self() {
            return this;
        }

        @Override
        public OciGenAiCohereChatModel build() {
            return new OciGenAiCohereChatModel(this);
        }
    }
}
