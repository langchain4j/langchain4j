package dev.langchain4j.model.gpullama3;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.beehive.gpullama3.Options;

import java.io.IOException;

import java.nio.file.Path;

import static dev.langchain4j.internal.Utils.getOrDefault;

public class GPULlama3StreamingChatModel extends GPULlama3BaseModel implements StreamingChatModel {



    private GPULlama3StreamingChatModel(Builder builder) {
        init(
                getOrDefault(builder.modelPath, Options.getDefaultOptions().modelPath()),
                getOrDefault(builder.temperature,  Double.valueOf(Options.getDefaultOptions().temperature())),
                getOrDefault(builder.topP, Double.valueOf(Options.getDefaultOptions().topp())),
                getOrDefault(builder.seed, Integer.valueOf((int) Options.getDefaultOptions().seed())),
                getOrDefault(builder.maxTokens, Options.getDefaultOptions().maxTokens()),
                getOrDefault(builder.onGPU, true),
                true
        );



    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        throw new RuntimeException("Not implemented");
    }


//    public void chat(String userMessage, StreamingChatResponseHandler handler) {
//        Options opts = getOptions(userMessage);
//
//        String finalResponse = model.runInstructOnceLangChain4J(
//                sampler,
//                opts,
//                token -> handler.onPartialResponse(token));
//
//        ChatResponse chatResponse  = ChatResponse.builder().aiMessage(AiMessage.from(finalResponse)).build();
//        handler.onCompleteResponse(chatResponse);
//    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        protected Path modelPath;
        protected Double temperature;
        protected Double topP;
        protected Integer seed;
        protected Integer maxTokens;
        protected Boolean onGPU;
        protected String modelName;
        protected Boolean stream;

        public Builder() {
            // This is public so it can be extended
        }

        public Builder modelPath(Path modelPath) {
            this.modelPath = modelPath;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder stream (Boolean stream) {
            this.stream = stream;
            return this;
        }

        public Builder onGPU (Boolean onGPU) {
            this.onGPU = onGPU;
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


        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }


        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }


        public GPULlama3StreamingChatModel build() {
            return new GPULlama3StreamingChatModel(this);
        }
    }



}
