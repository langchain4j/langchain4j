package dev.langchain4j.model.qianfan.client.chat;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ChatCompletionRequest {

    private final List<Message> messages;
    private final Double temperature;
    private final Double topP;
    private final Boolean stream;
    private final Double penaltyScore;
    private final String userId;
    private final List<Function> functions;
    private final String system;
    private final List<String> stop;
    private final Integer maxOutputTokens;

    private final String responseFormat;

    private ChatCompletionRequest(Builder builder) {
        this.messages = builder.messages;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.stream = builder.stream;
        this.penaltyScore = builder.penaltyScore;
        this.userId = builder.userId;
        this.functions = builder.functions;
        this.system = builder.system;
        this.responseFormat = builder.responseFormat;
        this.stop = builder.stop;
        this.maxOutputTokens=builder.maxOutputTokens;
    }


    public List<Message> messages() {
        return this.messages;
    }

    public Double temperature() {
        return this.temperature;
    }


    public Double topP() {
        return this.topP;
    }

    public Boolean stream() {
        return this.stream;
    }
    public String system() {
        return this.system;
    }


    public Double penaltyScore() {
        return this.penaltyScore;
    }


    public String userId() {
        return this.userId;
    }

    public List<Function> functions() {
        return this.functions;
    }


    @Override
    public String toString() {
        return "ChatCompletionRequest{" +
                "messages=" + messages +
                ", temperature=" + temperature +
                ", topP=" + topP +
                ", stream=" + stream +
                ", penaltyScore=" + penaltyScore +
                ", userId='" + userId + '\'' +
                ", functions=" + functions +
                ", system='" + system + '\'' +
                ", stop=" + stop +
                ", maxOutputTokens=" + maxOutputTokens +
                ", responseFormat='" + responseFormat + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private List<Message> messages;
        private Double temperature;
        private Double topP;
        private Boolean stream;
        private Double penaltyScore;
        private String userId;
        private List<Function> functions;
        private  String system;

        private  String responseFormat;
        private  List<String> stop;
        private  Integer maxOutputTokens;
        private Builder() {
        }

        public Builder from(
                ChatCompletionRequest instance) {
            this.messages(instance.messages);
            this.temperature(instance.temperature);
            this.topP(instance.topP);
            this.stream(instance.stream);
            this.penaltyScore(instance.penaltyScore);
            this.userId(instance.userId);
            this.functions(instance.functions);
            this.system(instance.system);
            this.responseFormat(instance.responseFormat);
            return this;
        }



        public Builder messages(List<Message> messages) {
            if (messages != null) {
                this.messages = Collections.unmodifiableList(messages);
            }
            return this;
        }

        public Builder messages(Message... messages) {
            return this.messages(Arrays.asList(messages));
        }

        public Builder addSystemMessage(String systemMessage) {
            if (this.messages == null) {
                this.messages = new ArrayList();
            }

            this.messages.add(Message.systemMessage(systemMessage));
            return this;
        }

        public Builder addUserMessage(String userMessage) {
            if (this.messages == null) {
                this.messages = new ArrayList();
            }

            this.messages.add(Message.userMessage(userMessage));
            return this;
        }

        public Builder addAssistantMessage(String assistantMessage) {
            if (this.messages == null) {
                this.messages = new ArrayList();
            }

            this.messages.add(Message.assistantMessage(assistantMessage));
            return this;
        }

        public Builder addFunctionMessage(String name, String content) {
            if (this.messages == null) {
                this.messages = new ArrayList();
            }

            this.messages.add(Message.functionMessage(name, content));
            return this;
        }


        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }
        public Builder system(String system) {
            this.system = system;
            return this;
        }
        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }
        public Builder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public Builder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }



        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }


        public Builder penaltyScore(Double penaltyScore) {
            this.penaltyScore = penaltyScore;
            return this;
        }


        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder functions(List<Function> functions) {
            if (functions != null) {
                this.functions = Collections.unmodifiableList(functions);
            }
            return this;
        }

        public Builder functions(Function... functions) {
            return this.functions(Arrays.asList(functions));
        }

        public Builder addFunction(Function function) {
            if (this.functions == null) {
                this.functions = new ArrayList();
            }

            this.functions.add(function);
            return this;
        }

        public ChatCompletionRequest build() {
            return new ChatCompletionRequest(this);
        }
    }
}
