package dev.langchain4j.model.zhipu.chat;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.model.zhipu.chat.ChatCompletionModel.GLM_4;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;


public final class ChatCompletionRequest {
    private final String model;
    private final List<Message> messages;
    @SerializedName("request_id")
    private final String requestId;
    @SerializedName("do_sample")
    private final String doSample;
    private final Boolean stream;
    private final Double temperature;
    @SerializedName("top_p")
    private final Double topP;
    @SerializedName("max_tokens")
    private final Integer maxTokens;
    private final List<String> stop;
    private final List<Tool> tools;
    @SerializedName("tool_choice")
    private final Object toolChoice;

    private ChatCompletionRequest(Builder builder) {
        this.model = builder.model;
        this.messages = builder.messages;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.requestId = builder.requestId;
        this.stream = builder.stream;
        this.stop = builder.stop;
        this.maxTokens = builder.maxTokens;
        this.doSample = builder.doSample;
        this.tools = builder.tools;
        this.toolChoice = builder.toolChoice;
    }

    public static Builder builder() {
        return new Builder();
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

    public Boolean stream() {
        return stream;
    }

    public List<String> stop() {
        return stop;
    }

    public Integer maxTokens() {
        return maxTokens;
    }

    public List<Tool> tools() {
        return tools;
    }

    public Object toolChoice() {
        return toolChoice;
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
                && Objects.equals(requestId, another.requestId)
                && Objects.equals(stream, another.stream)
                && Objects.equals(stop, another.stop)
                && Objects.equals(maxTokens, another.maxTokens)
                && Objects.equals(doSample, another.doSample)
                && Objects.equals(tools, another.tools)
                && Objects.equals(toolChoice, another.toolChoice);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(model);
        h += (h << 5) + Objects.hashCode(messages);
        h += (h << 5) + Objects.hashCode(temperature);
        h += (h << 5) + Objects.hashCode(topP);
        h += (h << 5) + Objects.hashCode(requestId);
        h += (h << 5) + Objects.hashCode(stream);
        h += (h << 5) + Objects.hashCode(stop);
        h += (h << 5) + Objects.hashCode(maxTokens);
        h += (h << 5) + Objects.hashCode(doSample);
        h += (h << 5) + Objects.hashCode(tools);
        h += (h << 5) + Objects.hashCode(toolChoice);
        return h;
    }

    @Override
    public String toString() {
        return "ChatCompletionRequest{"
                + "model=" + model
                + ", messages=" + messages
                + ", temperature=" + temperature
                + ", topP=" + topP
                + ", requestId=" + requestId
                + ", stream=" + stream
                + ", stop=" + stop
                + ", maxTokens=" + maxTokens
                + ", doSample=" + doSample
                + ", tools=" + tools
                + ", toolChoice=" + toolChoice
                + "}";
    }

    public static final class Builder {

        private String model = GLM_4.toString();
        private List<Message> messages;
        private Double temperature;
        private Double topP;
        private String requestId;
        private Boolean stream;
        private List<String> stop;
        private Integer maxTokens;
        private String doSample;
        private List<Tool> tools;
        private Object toolChoice;

        private Builder() {
        }

        public Builder from(ChatCompletionRequest instance) {
            model(instance.model);
            messages(instance.messages);
            temperature(instance.temperature);
            topP(instance.topP);
            requestId(instance.requestId);
            stream(instance.stream);
            stop(instance.stop);
            maxTokens(instance.maxTokens);
            doSample(instance.doSample);
            tools(instance.tools);
            toolChoice(instance.toolChoice);
            return this;
        }

        public Builder model(ChatCompletionModel model) {
            return model(model.toString());
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

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

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

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

        public Builder doSample(String doSample) {
            this.doSample = doSample;
            return this;
        }

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

        public ChatCompletionRequest build() {
            return new ChatCompletionRequest(this);
        }
    }
}
