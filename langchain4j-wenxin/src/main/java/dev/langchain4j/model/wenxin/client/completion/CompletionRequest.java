package dev.langchain4j.model.wenxin.client.completion;

import java.util.List;

public final class CompletionRequest {
    private final String prompt;
    private final Boolean stream;
    private final String user_id;

    private final Float temperature;
    private final Integer top_k;

    private final Float top_p;
    private final Float penalty_score;

    private final List<String> stop;
    private CompletionRequest(Builder builder) {
        this.prompt = builder.prompt;
        this.stream = builder.stream;
        this.user_id = builder.user_id;
        this.temperature = builder.temperature;
        this.top_k = builder.top_k;
        this.top_p = builder.top_p;
        this.penalty_score = builder.penalty_score;
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
        private String user_id;

        private  Float temperature;
        private  Integer top_k;

        private  Float top_p;
        private  Float penalty_score;

        private  List<String> stop;
        private Builder() {

        }

        public Builder from(
                CompletionRequest request) {
            this.prompt(request.prompt);
            this.stream(request.stream);
            this.user(request.user_id);
            this.prompt(request.prompt);
            this.stream(request.stream);
            this.user(request.user_id);
            this.temperature(request.temperature);
            this.top_k(request.top_k);
            this.top_p(request.top_p);
            this.penalty_score(request.penalty_score);

            return this;
        }

        public Builder temperature(Float temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder top_k(Integer top_k) {
            this.top_k = top_k;
            return this;
        }


        public Builder top_p(Float top_p) {
            this.top_p = top_p;
            return this;
        }

        public Builder penalty_score(Float penalty_score) {
            this.penalty_score = penalty_score;
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
            this.user_id = userId;
            return this;
        }

        public CompletionRequest build() {
            return new CompletionRequest(this);
        }
    }
}
