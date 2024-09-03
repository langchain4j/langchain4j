package dev.langchain4j.model.zhipu.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.model.zhipu.chat.ChatCompletionModel.GLM_4;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;


@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ChatCompletionRequest {
    private final String model;
    private final List<Message> messages;
    private final String requestId;
    private final String doSample;
    private final Boolean stream;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final List<String> stop;
    private final List<Tool> tools;
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

    public String getModel() {
        return model;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getDoSample() {
        return doSample;
    }

    public Boolean getStream() {
        return stream;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public List<String> getStop() {
        return stop;
    }

    public List<Tool> getTools() {
        return tools;
    }

    public Object getToolChoice() {
        return toolChoice;
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
