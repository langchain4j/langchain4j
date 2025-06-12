package dev.langchain4j.model.huggingface;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.huggingface.client.HuggingFaceClient;
import dev.langchain4j.model.huggingface.client.Options;
import dev.langchain4j.model.huggingface.client.Parameters;
import dev.langchain4j.model.huggingface.client.TextGenerationRequest;
import dev.langchain4j.model.huggingface.client.TextGenerationResponse;
import dev.langchain4j.model.huggingface.spi.HuggingFaceChatModelBuilderFactory;
import dev.langchain4j.model.huggingface.spi.HuggingFaceClientFactory;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.List;

public class HuggingFaceChatModel implements ChatModel {

    private final HuggingFaceClient client;
    private final Double temperature;
    private final Integer maxNewTokens;
    private final Boolean returnFullText;
    private final Boolean waitForModel;

    public HuggingFaceChatModel(
            String accessToken,
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

    public HuggingFaceChatModel(
            String baseUrl,
            String accessToken,
            String modelId,
            Duration timeout,
            Double temperature,
            Integer maxNewTokens,
            Boolean returnFullText,
            Boolean waitForModel) {
        this(HuggingFaceChatModel.builder()
                .baseUrl(baseUrl)
                .accessToken(accessToken)
                .modelId(modelId)
                .timeout(timeout)
                .temperature(temperature)
                .maxNewTokens(maxNewTokens)
                .returnFullText(returnFullText)
                .waitForModel(waitForModel));
    }

    public HuggingFaceChatModel(Builder builder) {
        this.client = FactoryCreator.FACTORY.create(new HuggingFaceClientFactory.Input() {
            @Override
            public String baseUrl() {
                return builder.baseUrl;
            }

            @Override
            public String apiKey() {
                return builder.accessToken;
            }

            @Override
            public String modelId() {
                return builder.modelId;
            }

            @Override
            public Duration timeout() {
                return builder.timeout;
            }
        });
        this.temperature = builder.temperature;
        this.maxNewTokens = builder.maxNewTokens;
        this.returnFullText = builder.returnFullText;
        this.waitForModel = builder.waitForModel;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        ChatRequestValidationUtils.validateMessages(chatRequest.messages());
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ChatRequestValidationUtils.validate(parameters.toolSpecifications());
        ChatRequestValidationUtils.validate(parameters.toolChoice());
        ChatRequestValidationUtils.validate(parameters.responseFormat());

        Response<AiMessage> response = generate(chatRequest.messages());

        return ChatResponse.builder()
                .aiMessage(response.content())
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(response.tokenUsage())
                        .finishReason(response.finishReason())
                        .build())
                .build();
    }

    private Response<AiMessage> generate(List<ChatMessage> messages) {

        TextGenerationRequest request = TextGenerationRequest.builder()
                .inputs(messages.stream().map(HuggingFaceChatModel::toText).collect(joining("\n")))
                .parameters(Parameters.builder()
                        .temperature(temperature)
                        .maxNewTokens(maxNewTokens)
                        .returnFullText(returnFullText)
                        .build())
                .options(Options.builder().waitForModel(waitForModel).build())
                .build();

        TextGenerationResponse textGenerationResponse = client.chat(request);

        return Response.from(AiMessage.from(textGenerationResponse.getGeneratedText()));
    }

    private static String toText(ChatMessage chatMessage) {
        if (chatMessage instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        } else if (chatMessage instanceof UserMessage userMessage) {
            return userMessage.singleText();
        } else if (chatMessage instanceof AiMessage aiMessage) {
            return aiMessage.text();
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + chatMessage.type());
        }
    }

    public static Builder builder() {
        for (HuggingFaceChatModelBuilderFactory factory : loadFactories(HuggingFaceChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    public static final class Builder {

        private String baseUrl;
        private String accessToken;
        private String modelId;
        private Duration timeout = Duration.ofSeconds(15);
        private Double temperature;
        private Integer maxNewTokens;
        private Boolean returnFullText = false;
        private Boolean waitForModel = true;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

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
            if (isNullOrBlank(accessToken)) {
                throw new IllegalArgumentException(
                        "HuggingFace access token must be defined. It can be generated here: https://huggingface.co/settings/tokens");
            }
            return new HuggingFaceChatModel(this);
        }
    }

    public static HuggingFaceChatModel withAccessToken(String accessToken) {
        return builder().accessToken(accessToken).build();
    }
}
