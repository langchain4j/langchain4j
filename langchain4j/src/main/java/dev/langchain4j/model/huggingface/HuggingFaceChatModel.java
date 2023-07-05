package dev.langchain4j.model.huggingface;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Result;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.huggingface.HuggingFaceModelName.TII_UAE_FALCON_7B_INSTRUCT;
import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;

public class HuggingFaceChatModel implements ChatLanguageModel {

    private final HuggingFaceClient client;
    private final Double temperature;
    private final Integer maxNewTokens;
    private final Boolean returnFullText;
    private final Boolean waitForModel;

    public HuggingFaceChatModel(String accessToken,
                                String modelId,
                                Duration timeout,
                                Double temperature,
                                Integer maxNewTokens,
                                Boolean returnFullText,
                                Boolean waitForModel) {
        this(HuggingFaceChatModel.builder()
                .accessToken(accessToken)
                .modelId(modelId)
                .timeout(timeout)
                .temperature(temperature)
                .maxNewTokens(maxNewTokens)
                .returnFullText(returnFullText)
                .waitForModel(waitForModel));
    }

    public HuggingFaceChatModel(Builder builder) {
        this.client = new HuggingFaceClient(builder.accessToken, builder.modelId, builder.timeout);
        this.temperature = builder.temperature;
        this.maxNewTokens = builder.maxNewTokens;
        this.returnFullText = builder.returnFullText;
        this.waitForModel = builder.waitForModel;
    }

    @Override
    public Result<AiMessage> sendUserMessage(String text) {
        return sendMessages(userMessage(text));
    }

    @Override
    public Result<AiMessage> sendUserMessage(Prompt userMessage) {
        return sendUserMessage(userMessage.text());
    }

    @Override
    public Result<AiMessage> sendUserMessage(Object structuredPrompt) {
        return sendUserMessage(toPrompt(structuredPrompt));
    }

    @Override
    public Result<AiMessage> sendMessages(ChatMessage... messages) {
        return sendMessages(asList(messages));
    }

    @Override
    public Result<AiMessage> sendMessages(List<ChatMessage> messages) {
        return sendMessages(messages, null);
    }

    @Override
    public Result<AiMessage> sendMessages(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {

        if (toolSpecifications != null && toolSpecifications.size() > 0) {
            throw new IllegalArgumentException("Tools are currently not supported for HuggingFace models");
        }

        TextGenerationRequest request = TextGenerationRequest.builder()
                .inputs(messages.stream()
                        .map(ChatMessage::text)
                        .collect(joining("\n")))
                .parameters(Parameters.builder()
                        .temperature(temperature)
                        .maxNewTokens(maxNewTokens)
                        .returnFullText(returnFullText)
                        .build())
                .options(Options.builder()
                        .waitForModel(waitForModel)
                        .build())
                .build();

        TextGenerationResponse textGenerationResponse = client.chat(request);

        AiMessage aiMessage = aiMessage(textGenerationResponse.generatedText());

        return Result.from(aiMessage);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String accessToken;
        private String modelId = TII_UAE_FALCON_7B_INSTRUCT;
        private Duration timeout = Duration.ofSeconds(15);
        private Double temperature;
        private Integer maxNewTokens;
        private Boolean returnFullText = false;
        private Boolean waitForModel = true;

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder modelId(String modelId) {
            if (modelId != null) {
                this.modelId = modelId;
            }
            return this;
        }

        public Builder timeout(Duration timeout) {
            if (timeout != null) {
                this.timeout = timeout;
            }
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxNewTokens(Integer maxNewTokens) {
            this.maxNewTokens = maxNewTokens;
            return this;
        }

        public Builder returnFullText(Boolean returnFullText) {
            if (returnFullText != null) {
                this.returnFullText = returnFullText;
            }
            return this;
        }

        public Builder waitForModel(Boolean waitForModel) {
            if (waitForModel != null) {
                this.waitForModel = waitForModel;
            }
            return this;
        }

        public HuggingFaceChatModel build() {
            if (accessToken == null || accessToken.trim().isEmpty()) {
                throw new IllegalArgumentException("HuggingFace access token must be defined. It can be generated here: https://huggingface.co/settings/tokens");
            }
            return new HuggingFaceChatModel(this);
        }
    }
}
