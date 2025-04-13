package dev.langchain4j.model.oracle.oci.genai;

import static java.util.function.Predicate.not;

import com.oracle.bmc.http.client.Serializer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chat models hosted on OCI GenAI.
 * <p>OCI Generative AI is a fully managed service that provides a set of state-of-the-art,
 * customizable large language models (LLMs) that cover a wide range of use cases for text
 * generation, summarization, and text embeddings.
 *
 * <p>To learn more about the service, see the <a href="https://docs.oracle.com/iaas/Content/generative-ai/home.htm">Generative AI documentation</a>
 */
public class OciGenAiStreamingChatModel extends BaseGenericChatModel<OciGenAiStreamingChatModel>
        implements StreamingChatModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(OciGenAiStreamingChatModel.class);
    private final Builder builder;

    OciGenAiStreamingChatModel(Builder builder) {
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
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        var bmcChatRequest = prepareRequest(chatRequest).isStream(true).build();

        try (var isr = new InputStreamReader(super.ociChat(bmcChatRequest).getEventStream());
                var reader = new BufferedReader(isr)) {
            String line;
            var streamingResponseBuilder = new GenericStreamingResponseBuilder(builder.chatModelId());
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                LOGGER.debug("Partial response: {}", line);
                var chatChoice = Serializer.getDefault()
                        .readValue(
                                line.replaceFirst("data: ", ""),
                                com.oracle.bmc.generativeaiinference.model.ChatChoice.class);

                streamingResponseBuilder.append(chatChoice);

                Optional.ofNullable(OciGenAiChatModel.map(chatChoice, builder.chatModelId())
                                .aiMessage())
                        .map(AiMessage::text)
                        .filter(not(String::isEmpty))
                        .ifPresent(handler::onPartialResponse);
            }

            handler.onCompleteResponse(streamingResponseBuilder.build());
        } catch (Exception e) {
            handler.onError(e);
        }
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
    public static class Builder extends BaseGenericChatModel.Builder<OciGenAiStreamingChatModel, Builder> {

        Builder() {}

        @Override
        Builder self() {
            return this;
        }

        public OciGenAiStreamingChatModel build() {
            return new OciGenAiStreamingChatModel(this);
        }
    }
}
