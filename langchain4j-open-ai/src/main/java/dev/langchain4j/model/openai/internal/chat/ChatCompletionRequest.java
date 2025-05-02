package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.model.openai.internal.shared.StreamOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;

@JsonDeserialize(builder = ChatCompletionRequest.Builder.class)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class ChatCompletionRequest {

    @JsonProperty
    private final String model;
    @JsonProperty
    private final List<Message> messages;
    @JsonProperty
    private final Double temperature;
    @JsonProperty
    private final Double topP;
    @JsonProperty
    private final Integer n;
    @JsonProperty
    private final Boolean stream;
    @JsonProperty
    private final StreamOptions streamOptions;
    @JsonProperty
    private final List<String> stop;
    @JsonProperty
    private final Integer maxTokens;
    @JsonProperty
    private final Integer maxCompletionTokens;
    @JsonProperty
    private final Double presencePenalty;
    @JsonProperty
    private final Double frequencyPenalty;
    @JsonProperty
    private final Map<String, Integer> logitBias;
    @JsonProperty
    private final String user;
    @JsonProperty
    private final ResponseFormat responseFormat;
    @JsonProperty
    private final Integer seed;
    @JsonProperty
    private final List<Tool> tools;
    @JsonProperty
    private final Object toolChoice;
    @JsonProperty
    private final Boolean parallelToolCalls;
    @JsonProperty
    private final Boolean store;
    @JsonProperty
    private final Map<String, String> metadata;
    @JsonProperty
    private final String reasoningEffort;
    @JsonProperty
    private final String serviceTier;
    @JsonProperty
    @Deprecated
    private final List<Function> functions;
    @JsonProperty
    @Deprecated
    private final FunctionCall functionCall;

    public ChatCompletionRequest(Builder builder) {
        this.model = builder.model;
        this.messages = builder.messages;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.n = builder.n;
        this.stream = builder.stream;
        this.streamOptions = builder.streamOptions;
        this.stop = builder.stop;
        this.maxTokens = builder.maxTokens;
        this.maxCompletionTokens = builder.maxCompletionTokens;
        this.presencePenalty = builder.presencePenalty;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.logitBias = builder.logitBias;
        this.user = builder.user;
        this.responseFormat = builder.responseFormat;
        this.seed = builder.seed;
        this.tools = builder.tools;
        this.toolChoice = builder.toolChoice;
        this.parallelToolCalls = builder.parallelToolCalls;
        this.store = builder.store;
        this.metadata = builder.metadata;
        this.reasoningEffort = builder.reasoningEffort;
        this.serviceTier = builder.serviceTier;
        this.functions = builder.functions;
        this.functionCall = builder.functionCall;
    }

    public String model() {
        return model;
    }

    public List<Message> messages() {
        return messages;
    }

    public Double temperature() {
        return temperature;
    }

    public Double topP() {
        return topP;
    }

    public Integer n() {
        return n;
    }

    public Boolean stream() {
        return stream;
    }

    public StreamOptions streamOptions() {
        return streamOptions;
    }

    public List<String> stop() {
        return stop;
    }

    public Integer maxTokens() {
        return maxTokens;
    }

    public Integer maxCompletionTokens() {
        return maxCompletionTokens;
    }

    public Double presencePenalty() {
        return presencePenalty;
    }

    public Double frequencyPenalty() {
        return frequencyPenalty;
    }

    public Map<String, Integer> logitBias() {
        return logitBias;
    }

    public String user() {
        return user;
    }

    public ResponseFormat responseFormat() {
        return responseFormat;
    }

    public Integer seed() {
        return seed;
    }

    public List<Tool> tools() {
        return tools;
    }

    public Object toolChoice() {
        return toolChoice;
    }

    public Boolean parallelToolCalls() {
        return parallelToolCalls;
    }

    public Boolean store() {
        return store;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    public String reasoningEffort() {
        return reasoningEffort;
    }

    public String serviceTier() {
        return serviceTier;
    }

    @Deprecated
    public List<Function> functions() {
        return functions;
    }

    @Deprecated
    public FunctionCall functionCall() {
        return functionCall;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ChatCompletionRequest
                && equalTo((ChatCompletionRequest) another);
    }

    private boolean equalTo(ChatCompletionRequest another) {
        return Objects.equals(model, another.model)
                && Objects.equals(messages, another.messages)
                && Objects.equals(temperature, another.temperature)
                && Objects.equals(topP, another.topP)
                && Objects.equals(n, another.n)
                && Objects.equals(stream, another.stream)
                && Objects.equals(streamOptions, another.streamOptions)
                && Objects.equals(stop, another.stop)
                && Objects.equals(maxTokens, another.maxTokens)
                && Objects.equals(maxCompletionTokens, another.maxCompletionTokens)
                && Objects.equals(presencePenalty, another.presencePenalty)
                && Objects.equals(frequencyPenalty, another.frequencyPenalty)
                && Objects.equals(logitBias, another.logitBias)
                && Objects.equals(user, another.user)
                && Objects.equals(responseFormat, another.responseFormat)
                && Objects.equals(seed, another.seed)
                && Objects.equals(tools, another.tools)
                && Objects.equals(toolChoice, another.toolChoice)
                && Objects.equals(parallelToolCalls, another.parallelToolCalls)
                && Objects.equals(store, another.store)
                && Objects.equals(metadata, another.metadata)
                && Objects.equals(reasoningEffort, another.reasoningEffort)
                && Objects.equals(serviceTier, another.serviceTier)
                && Objects.equals(functions, another.functions)
                && Objects.equals(functionCall, another.functionCall);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(model);
        h += (h << 5) + Objects.hashCode(messages);
        h += (h << 5) + Objects.hashCode(temperature);
        h += (h << 5) + Objects.hashCode(topP);
        h += (h << 5) + Objects.hashCode(n);
        h += (h << 5) + Objects.hashCode(stream);
        h += (h << 5) + Objects.hashCode(streamOptions);
        h += (h << 5) + Objects.hashCode(stop);
        h += (h << 5) + Objects.hashCode(maxTokens);
        h += (h << 5) + Objects.hashCode(maxCompletionTokens);
        h += (h << 5) + Objects.hashCode(presencePenalty);
        h += (h << 5) + Objects.hashCode(frequencyPenalty);
        h += (h << 5) + Objects.hashCode(logitBias);
        h += (h << 5) + Objects.hashCode(user);
        h += (h << 5) + Objects.hashCode(responseFormat);
        h += (h << 5) + Objects.hashCode(seed);
        h += (h << 5) + Objects.hashCode(tools);
        h += (h << 5) + Objects.hashCode(toolChoice);
        h += (h << 5) + Objects.hashCode(parallelToolCalls);
        h += (h << 5) + Objects.hashCode(store);
        h += (h << 5) + Objects.hashCode(metadata);
        h += (h << 5) + Objects.hashCode(reasoningEffort);
        h += (h << 5) + Objects.hashCode(serviceTier);
        h += (h << 5) + Objects.hashCode(functions);
        h += (h << 5) + Objects.hashCode(functionCall);
        return h;
    }

    @Override
    public String toString() {
        return "ChatCompletionRequest{"
                + "model=" + model
                + ", messages=" + messages
                + ", temperature=" + temperature
                + ", topP=" + topP
                + ", n=" + n
                + ", stream=" + stream
                + ", streamOptions=" + streamOptions
                + ", stop=" + stop
                + ", maxTokens=" + maxTokens
                + ", maxCompletionTokens=" + maxCompletionTokens
                + ", presencePenalty=" + presencePenalty
                + ", frequencyPenalty=" + frequencyPenalty
                + ", logitBias=" + logitBias
                + ", user=" + user
                + ", responseFormat=" + responseFormat
                + ", seed=" + seed
                + ", tools=" + tools
                + ", toolChoice=" + toolChoice
                + ", parallelToolCalls=" + parallelToolCalls
                + ", store=" + store
                + ", metadata=" + metadata
                + ", reasoningEffort=" + reasoningEffort
                + ", serviceTier=" + serviceTier
                + ", functions=" + functions
                + ", functionCall=" + functionCall
                + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private String model;
        private List<Message> messages;
        private Double temperature;
        private Double topP;
        private Integer n;
        private Boolean stream;
        private StreamOptions streamOptions;
        private List<String> stop;
        private Integer maxTokens;
        private Integer maxCompletionTokens;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Map<String, Integer> logitBias;
        private String user;
        private ResponseFormat responseFormat;
        private Integer seed;
        private List<Tool> tools;
        private Object toolChoice;
        private Boolean parallelToolCalls;
        private Boolean store;
        private Map<String, String> metadata;
        private String reasoningEffort;
        private String serviceTier;
        @Deprecated
        private List<Function> functions;
        @Deprecated
        private FunctionCall functionCall;

        public Builder from(ChatCompletionRequest instance) {
            model(instance.model);
            messages(instance.messages);
            temperature(instance.temperature);
            topP(instance.topP);
            n(instance.n);
            stream(instance.stream);
            streamOptions(instance.streamOptions);
            stop(instance.stop);
            maxTokens(instance.maxTokens);
            maxCompletionTokens(instance.maxCompletionTokens);
            presencePenalty(instance.presencePenalty);
            frequencyPenalty(instance.frequencyPenalty);
            logitBias(instance.logitBias);
            user(instance.user);
            responseFormat(instance.responseFormat);
            seed(instance.seed);
            tools(instance.tools);
            toolChoice(instance.toolChoice);
            parallelToolCalls(instance.parallelToolCalls);
            store(instance.store);
            metadata(instance.metadata);
            reasoningEffort(instance.reasoningEffort);
            serviceTier(instance.serviceTier);
            functions(instance.functions);
            functionCall(instance.functionCall);
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        @JsonSetter
        public Builder messages(List<Message> messages) {
            if (messages != null) {
                this.messages = unmodifiableList(messages);
            }
            return this;
        }

        public Builder messages(Message... messages) {
            return messages(asList(messages));
        }

        public Builder addSystemMessage(String systemMessage) {
            if (this.messages == null) {
                this.messages = new ArrayList<>();
            }
            this.messages.add(SystemMessage.from(systemMessage));
            return this;
        }

        public Builder addUserMessage(String userMessage) {
            if (this.messages == null) {
                this.messages = new ArrayList<>();
            }
            this.messages.add(UserMessage.from(userMessage));
            return this;
        }

        public Builder addAssistantMessage(String assistantMessage) {
            if (this.messages == null) {
                this.messages = new ArrayList<>();
            }
            this.messages.add(AssistantMessage.from(assistantMessage));
            return this;
        }

        public Builder addToolMessage(String toolCallId, String content) {
            if (this.messages == null) {
                this.messages = new ArrayList<>();
            }
            this.messages.add(ToolMessage.from(toolCallId, content));
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder n(Integer n) {
            this.n = n;
            return this;
        }

        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        public Builder streamOptions(StreamOptions streamOptions) {
            this.streamOptions = streamOptions;
            return this;
        }

        @JsonSetter
        public Builder stop(List<String> stop) {
            if (stop != null) {
                this.stop = unmodifiableList(stop);
            }
            return this;
        }

        public Builder stop(String... stop) {
            return stop(asList(stop));
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder maxCompletionTokens(Integer maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder logitBias(Map<String, Integer> logitBias) {
            if (logitBias != null) {
                this.logitBias = unmodifiableMap(logitBias);
            }
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder responseFormat(ResponseFormatType responseFormatType) {
            if (responseFormatType != null) {
                responseFormat = ResponseFormat.builder()
                        .type(responseFormatType)
                        .build();
            }
            return this;
        }

        @JsonSetter
        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        @JsonSetter
        public Builder tools(List<Tool> tools) {
            if (tools != null) {
                this.tools = unmodifiableList(tools);
            }
            return this;
        }

        public Builder tools(Tool... tools) {
            return tools(asList(tools));
        }

        public Builder toolChoice(ToolChoiceMode toolChoiceMode) {
            this.toolChoice = toolChoiceMode;
            return this;
        }

        public Builder toolChoice(String functionName) {
            return toolChoice(ToolChoice.from(functionName));
        }

        public Builder toolChoice(Object toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public Builder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public Builder store(Boolean store) {
            this.store = store;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            if (metadata != null) {
                this.metadata = unmodifiableMap(metadata);
            }
            return this;
        }

        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        @Deprecated
        public Builder functions(Function... functions) {
            return functions(asList(functions));
        }

        @JsonSetter
        @Deprecated
        public Builder functions(List<Function> functions) {
            if (functions != null) {
                this.functions = unmodifiableList(functions);
            }
            return this;
        }

        @Deprecated
        public Builder functionCall(String functionName) {
            if (functionName != null) {
                this.functionCall = FunctionCall.builder()
                        .name(functionName)
                        .build();
            }
            return this;
        }

        @Deprecated
        public Builder functionCall(FunctionCall functionCall) {
            this.functionCall = functionCall;
            return this;
        }

        public ChatCompletionRequest build() {
            return new ChatCompletionRequest(this);
        }
    }
}
