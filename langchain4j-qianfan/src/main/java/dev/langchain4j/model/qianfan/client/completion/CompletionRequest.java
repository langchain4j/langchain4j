package dev.langchain4j.model.qianfan.client.completion;

import java.util.List;

public final class CompletionRequest {
    private final String prompt;
    private final Boolean stream;
    private final String userId;

    private final Double temperature;
    private final Integer topK;

    private final Double topP;
    private final Double penaltyScore;

    private final List<String> stop;
    private CompletionRequest(Builder builder) {
        this.prompt = builder.prompt;
        this.stream = builder.stream;
        this.userId = builder.userId;
        this.temperature = builder.temperature;
        this.topK = builder.topK;
        this.topP = builder.topP;
        this.penaltyScore = builder.penaltyScore;
        this.stop = builder.stop;
    }


    public String prompt() {
        return this.prompt;
    }



    public Boolean stream() {
        return this.stream;
    }




    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String prompt;
        private Boolean stream;
        private String userId;

        private  Double temperature;
        private  Integer topK;

        private  Double topP;
        private  Double penaltyScore;

        private  List<String> stop;
        private Builder() {

        }

        public Builder from(
                CompletionRequest request) {
            this.prompt(request.prompt);
            this.stream(request.stream);
            this.user(request.userId);
            this.prompt(request.prompt);
            this.stream(request.stream);
            this.user(request.userId);
            this.temperature(request.temperature);
            this.topK(request.topK);
            this.topP(request.topP);
            this.penaltyScore(request.penaltyScore);

            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }


        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder penaltyScore(Double penaltyScore) {
            this.penaltyScore = penaltyScore;
            return this;
        }
        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        public Builder user(String userId) {
            this.userId = userId;
            return this;
        }

        public CompletionRequest build() {
            return new CompletionRequest(this);
        }
    }
}
