package dev.langchain4j.model.watsonx;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.model.ModelProvider.WATSONX;
import static java.util.Arrays.asList;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toCollection;

import com.ibm.watsonx.ai.chat.BaseChatRequest;
import com.ibm.watsonx.ai.chat.ChatHandler;
import com.ibm.watsonx.ai.chat.ChatProvider;
import com.ibm.watsonx.ai.chat.TextChatResponse;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.CompletedToolCall;
import com.ibm.watsonx.ai.chat.model.PartialChatResponse;
import com.ibm.watsonx.ai.chat.model.PartialToolCall;
import com.ibm.watsonx.ai.chat.model.Tool;
import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Internal
abstract class WatsonxChatBase<R extends BaseChatRequest> {

    private static final StreamingHandle CANCELLATION_UNSUPPORTED = new CancellationUnsupportedStreamingHandle();

    protected final List<ChatModelListener> listeners;
    protected final ChatRequestParameters defaultRequestParameters;
    protected final Set<Capability> supportedCapabilities;
    protected final boolean strictJsonSchema;

    protected WatsonxChatBase(Builder<?> builder, ChatRequestParameters defaultRequestParameters) {
        this.listeners = copy(builder.listeners);
        this.supportedCapabilities = copy(builder.supportedCapabilities);
        this.defaultRequestParameters = defaultRequestParameters;
        this.strictJsonSchema = getOrDefault(builder.strictJsonSchema, true);
    }

    protected abstract ChatProvider<R, ? extends TextChatResponse> chatProvider();

    protected abstract R buildChatRequest(
            List<ChatMessage> messages, List<Tool> tools, ChatRequestParameters parameters);

    public List<ChatModelListener> listeners() {
        return listeners;
    }

    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    public ModelProvider provider() {
        return WATSONX;
    }

    protected final ChatResponse executeChat(ChatRequest chatRequest) {

        var watsonxChatRequest = toWatsonxChatRequest(chatRequest);
        TextChatResponse chatResponse = WatsonxExceptionMapper.INSTANCE.withExceptionMapper(
                () -> chatProvider().chat(watsonxChatRequest));

        String refusal = chatResponse.toAssistantMessage().refusal();

        if (isNotNullOrBlank(refusal)) throw new ContentFilteredException(refusal);

        return Converter.toChatResponse(chatResponse);
    }

    protected final void executeChatStreaming(ChatRequest chatRequest, StreamingChatResponseHandler handler) {

        var watsonxChatRequest = toWatsonxChatRequest(chatRequest);

        chatProvider().chatStreaming(watsonxChatRequest, new ChatHandler() {
            @Override
            public void onCompleteResponse(com.ibm.watsonx.ai.chat.ChatResponse completeResponse) {

                TextChatResponse textChatResponse = (TextChatResponse) completeResponse;
                ChatResponse chatResponse;

                try {
                    String refusal = textChatResponse.toAssistantMessage().refusal();

                    if (isNotNullOrBlank(refusal)) {
                        handler.onError(new ContentFilteredException(refusal));
                        return;
                    }

                    chatResponse = Converter.toChatResponse(textChatResponse);
                } catch (RuntimeException e) {
                    handler.onError(WatsonxExceptionMapper.INSTANCE.mapException(e));
                    return;
                }

                handler.onCompleteResponse(chatResponse);
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(WatsonxExceptionMapper.INSTANCE.mapException(error));
            }

            @Override
            public void onPartialResponse(String partialResponse, PartialChatResponse partialChatResponse) {
                InternalStreamingChatResponseHandlerUtils.onPartialResponse(
                        handler, partialResponse, CANCELLATION_UNSUPPORTED);
            }

            @Override
            public void onCompleteToolCall(CompletedToolCall completedToolCall) {
                handler.onCompleteToolCall(Converter.toCompleteToolCall(completedToolCall.toolCall()));
            }

            @Override
            public void onPartialThinking(String partialThinking, PartialChatResponse partialChatResponse) {
                InternalStreamingChatResponseHandlerUtils.onPartialThinking(
                        handler, partialThinking, CANCELLATION_UNSUPPORTED);
            }

            @Override
            public void onPartialToolCall(PartialToolCall partialToolCall) {
                InternalStreamingChatResponseHandlerUtils.onPartialToolCall(
                        handler, Converter.toPartialToolCall(partialToolCall), CANCELLATION_UNSUPPORTED);
            }
        });
    }

    protected static final void applyCommonParameters(
            DefaultChatRequestParameters.Builder<?> target, Builder<?> builder) {
        ChatRequestParameters common = DefaultChatRequestParameters.EMPTY;
        if (nonNull(builder.defaultRequestParameters)) {
            validate(builder.defaultRequestParameters);
            common = builder.defaultRequestParameters;
        }

        target.modelName(getOrDefault(builder.modelName, common.modelName()));
        target.temperature(getOrDefault(builder.temperature, common.temperature()));
        target.topP(getOrDefault(builder.topP, common.topP()));
        target.frequencyPenalty(getOrDefault(builder.frequencyPenalty, common.frequencyPenalty()));
        target.presencePenalty(getOrDefault(builder.presencePenalty, common.presencePenalty()));
        target.maxOutputTokens(getOrDefault(builder.maxOutputTokens, common.maxOutputTokens()));
        target.stopSequences(getOrDefault(builder.stopSequences, common.stopSequences()));
        target.toolSpecifications(getOrDefault(builder.toolSpecifications, common.toolSpecifications()));
        target.toolChoice(getOrDefault(builder.toolChoice, common.toolChoice()));
        target.responseFormat(getOrDefault(builder.responseFormat, common.responseFormat()));
    }

    private R toWatsonxChatRequest(ChatRequest chatRequest) {

        validate(chatRequest.parameters());

        List<ToolSpecification> toolSpecifications = getOrDefault(
                chatRequest.parameters().toolSpecifications(), defaultRequestParameters.toolSpecifications());

        List<ChatMessage> messages =
                chatRequest.messages().stream().map(Converter::toChatMessage).collect(toCollection(ArrayList::new));

        List<Tool> tools = nonNull(toolSpecifications) && !toolSpecifications.isEmpty()
                ? toolSpecifications.stream().map(Converter::toTool).toList()
                : null;

        return buildChatRequest(messages, tools, chatRequest.parameters());
    }

    private static void validate(ChatRequestParameters parameters) {
        if (nonNull(parameters.topK()))
            throw new UnsupportedFeatureException("'topK' parameter is not supported by watsonx.ai");
    }

    private static final class CancellationUnsupportedStreamingHandle implements StreamingHandle {

        @Override
        public void cancel() {
            throw new UnsupportedFeatureException("Streaming cancellation is not supported by watsonx.ai");
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    abstract static class Builder<T extends Builder<T>> extends WatsonxConnectionBuilder<T> {
        protected String modelName;
        protected Double temperature;
        protected Double topP;
        protected Double frequencyPenalty;
        protected Double presencePenalty;
        protected Integer maxOutputTokens;
        protected List<String> stopSequences;
        protected ToolChoice toolChoice;
        protected ResponseFormat responseFormat;
        protected Boolean strictJsonSchema;
        protected List<ToolSpecification> toolSpecifications;
        protected List<ChatModelListener> listeners;
        protected ChatRequestParameters defaultRequestParameters;
        protected Set<Capability> supportedCapabilities;
        protected Map<String, Integer> logitBias;
        protected Boolean logprobs;
        protected Integer topLogprobs;
        protected Integer seed;
        protected String toolChoiceName;

        /**
         * Sets the sampling temperature in the range {@code [0.0, 2.0]}.
         *
         * @param temperature the sampling temperature
         * @return {@code this}
         */
        public T temperature(Double temperature) {
            this.temperature = temperature;
            return (T) this;
        }

        /**
         * Sets the nucleus sampling probability in the range {@code (0.0, 1.0]}
         *
         * @param topP the nucleus sampling threshold
         * @return {@code this}
         */
        public T topP(Double topP) {
            this.topP = topP;
            return (T) this;
        }

        /**
         * Sets the frequency penalty in the range {@code [-2.0, 2.0]}.
         *
         * @param frequencyPenalty the frequency penalty
         * @return {@code this}
         */
        public T frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return (T) this;
        }

        /**
         * Sets the presence penalty in the range {@code [-2.0, 2.0]}.
         *
         * @param presencePenalty the presence penalty
         * @return {@code this}
         */
        public T presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return (T) this;
        }

        /**
         * Sets the maximum number of tokens to generate in the response.
         *
         * @param maxOutputTokens the maximum number of output tokens
         * @return {@code this}
         */
        public T maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return (T) this;
        }

        /**
         * Sets the sequences that will stop generation when encountered.
         *
         * @param stopSequences the stop sequences
         * @return {@code this}
         */
        public T stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return (T) this;
        }

        /**
         * Sets the sequences that will stop generation when encountered.
         *
         * @param stopSequences the stop sequences
         * @return {@code this}
         */
        public T stopSequences(String... stopSequences) {
            return stopSequences(asList(stopSequences));
        }

        /**
         * Sets how the model selects tools. Controls whether tool use is automatic, forced, or disabled.
         *
         * @param toolChoice the tool choice mode
         * @return {@code this}
         */
        public T toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return (T) this;
        }

        /**
         * Sets the response format to control structured output, e.g. JSON mode.
         *
         * @param responseFormat the response format
         * @return {@code this}
         */
        public T responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return (T) this;
        }

        /**
         * Enables the strict mode for the JSON Schema used by structured outputs. Defaults to {@code true}.
         *
         * <p>In strict mode the model is required to adhere to the schema, every property is marked as
         * {@code required} and {@code additionalProperties} is set to {@code false}. Set it to {@code false} to let
         * the model treat the schema as a hint instead of a constraint.
         *
         * @param strictJsonSchema {@code true} to enable the strict mode
         * @return {@code this}
         */
        public T strictJsonSchema(Boolean strictJsonSchema) {
            this.strictJsonSchema = strictJsonSchema;
            return (T) this;
        }

        /**
         * Sets the tool definitions available to the model for function calling.
         *
         * @param toolSpecifications the list of tool specifications
         * @return {@code this}
         */
        public T toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return (T) this;
        }

        /**
         * Sets the tool definitions available to the model for function calling.
         *
         * @param toolSpecifications the tool specifications
         * @return {@code this}
         */
        public T toolSpecifications(ToolSpecification... toolSpecifications) {
            return toolSpecifications(asList(toolSpecifications));
        }

        /**
         * Sets the list of {@link ChatModelListener} instances for observing chat model interactions.
         *
         * @param listeners the listeners to register
         * @return {@code this}
         */
        public T listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return (T) this;
        }

        /**
         * Sets default request parameters that are merged into every chat request.
         *
         * @param defaultRequestParameters the default request parameters
         * @return {@code this}
         */
        public T defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return (T) this;
        }

        /**
         * Declares the capabilities supported by this model instance.
         *
         * @param supportedCapabilities the set of supported capabilities
         * @return {@code this}
         */
        public T supportedCapabilities(Set<Capability> supportedCapabilities) {
            this.supportedCapabilities = supportedCapabilities;
            return (T) this;
        }

        /**
         * Declares the capabilities supported by this model instance.
         *
         * @param supportedCapabilities the supported capabilities
         * @return {@code this}
         */
        public T supportedCapabilities(Capability... supportedCapabilities) {
            return supportedCapabilities(new HashSet<>(asList(supportedCapabilities)));
        }

        /**
         * Sets per-token logit biases to increase or decrease the likelihood of specific tokens. Keys are token IDs; values are bias offsets in the
         * range {@code [-100, 100]}.
         *
         * @param logitBias the logit bias map
         * @return {@code this}
         */
        public T logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return (T) this;
        }

        /**
         * Enables returning log probabilities of the output tokens.
         *
         * @param logprobs {@code true} to include log probabilities in the response
         * @return {@code this}
         */
        public T logprobs(Boolean logprobs) {
            this.logprobs = logprobs;
            return (T) this;
        }

        /**
         * Sets the number of most likely tokens to return log probabilities for at each position. Requires {@link #logprobs} to be {@code true}.
         * Value must be between 0 and 20.
         *
         * @param topLogprobs the number of top log probabilities to return
         * @return {@code this}
         */
        public T topLogprobs(Integer topLogprobs) {
            this.topLogprobs = topLogprobs;
            return (T) this;
        }

        /**
         * Sets the random seed for deterministic sampling. Using the same seed and parameters should produce the same output across calls.
         *
         * @param seed the random seed
         * @return {@code this}
         */
        public T seed(Integer seed) {
            this.seed = seed;
            return (T) this;
        }

        /**
         * Sets the name of the specific tool to force when {@link #toolChoice} is set to force a particular tool.
         *
         * @param toolChoiceName the tool name to force
         * @return {@code this}
         */
        public T toolChoiceName(String toolChoiceName) {
            this.toolChoiceName = toolChoiceName;
            return (T) this;
        }
    }
}
