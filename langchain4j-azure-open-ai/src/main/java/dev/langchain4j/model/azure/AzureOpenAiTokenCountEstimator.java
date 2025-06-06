package dev.langchain4j.model.azure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.azure.AzureOpenAiChatModelName.GPT_3_5_TURBO;
import static dev.langchain4j.model.azure.AzureOpenAiChatModelName.GPT_3_5_TURBO_0301;
import static dev.langchain4j.model.azure.AzureOpenAiChatModelName.GPT_3_5_TURBO_1106;
import static dev.langchain4j.model.azure.AzureOpenAiChatModelName.GPT_4_0125_PREVIEW;
import static dev.langchain4j.model.azure.AzureOpenAiChatModelName.GPT_4_1106_PREVIEW;
import static dev.langchain4j.model.azure.AzureOpenAiChatModelName.GPT_4_TURBO;
import static dev.langchain4j.model.azure.AzureOpenAiChatModelName.GPT_4_VISION_PREVIEW;

/**
 * This class can be used to estimate the cost (in tokens) before calling OpenAI or when using streaming.
 * Magic numbers present in this class were found empirically while testing.
 * There are integration tests in place that are making sure that the calculations here are very close to that of OpenAI.
 */
public class AzureOpenAiTokenCountEstimator implements TokenCountEstimator {

    private final String modelName;
    private final Optional<Encoding> encoding;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Creates an instance of the {@code AzureOpenAiTokenCountEstimator} for the "gpt-3.5-turbo" model.
     *
     * @deprecated Please use other constructors and specify the model name explicitly.
     */
    @Deprecated(forRemoval = true)
    public AzureOpenAiTokenCountEstimator() {
        this(GPT_3_5_TURBO.modelType());
    }

    /**
     * Creates an instance of the {@code AzureOpenAiTokenCountEstimator} for a given {@link AzureOpenAiChatModelName}.
     */
    public AzureOpenAiTokenCountEstimator(AzureOpenAiChatModelName modelName) {
        this(modelName.modelType());
    }

    /**
     * Creates an instance of the {@code AzureOpenAiTokenCountEstimator} for a given {@link AzureOpenAiEmbeddingModelName}.
     */
    public AzureOpenAiTokenCountEstimator(AzureOpenAiEmbeddingModelName modelName) {
        this(modelName.modelType());
    }

    /**
     * Creates an instance of the {@code AzureOpenAiTokenCountEstimator} for a given {@link AzureOpenAiLanguageModelName}.
     */
    public AzureOpenAiTokenCountEstimator(AzureOpenAiLanguageModelName modelName) {
        this(modelName.modelType());
    }

    /**
     * Creates an instance of the {@code AzureOpenAiTokenCountEstimator} for a given model name.
     */
    public AzureOpenAiTokenCountEstimator(String modelName) {
        this.modelName = ensureNotBlank(modelName, "modelName");
        // If the model is unknown, we should NOT fail fast during the creation of AzureOpenAiTokenCountEstimator.
        // Doing so would cause the failure of every OpenAI***Model that uses this token count estimator.
        // This is done to account for situations when a new OpenAI model is available,
        // but JTokkit does not yet support it.
        this.encoding = Encodings.newLazyEncodingRegistry().getEncodingForModel(modelName);
    }

    public int estimateTokenCountInText(String text) {
        return encoding.orElseThrow(unknownModelException())
                .countTokensOrdinary(text);
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        int tokenCount = 1; // 1 token for role
        tokenCount += extraTokensPerMessage();

        if (message instanceof SystemMessage) {
            tokenCount += estimateTokenCountIn((SystemMessage) message);
        } else if (message instanceof UserMessage) {
            tokenCount += estimateTokenCountIn((UserMessage) message);
        } else if (message instanceof AiMessage) {
            tokenCount += estimateTokenCountIn((AiMessage) message);
        } else if (message instanceof ToolExecutionResultMessage) {
            tokenCount += estimateTokenCountIn((ToolExecutionResultMessage) message);
        } else {
            throw new IllegalArgumentException("Unknown message type: " + message);
        }

        return tokenCount;
    }

    private int estimateTokenCountIn(SystemMessage systemMessage) {
        return estimateTokenCountInText(systemMessage.text());
    }

    private int estimateTokenCountIn(UserMessage userMessage) {
        int tokenCount = 0;

        for (Content content : userMessage.contents()) {
            if (content instanceof TextContent) {
                tokenCount += estimateTokenCountInText(((TextContent) content).text());
            } else if (content instanceof ImageContent) {
                tokenCount += 85; // TODO implement for HIGH/AUTO detail level
            } else {
                throw illegalArgument("Unknown content type: " + content);
            }
        }

        if (userMessage.name() != null && !modelName.equals(GPT_4_VISION_PREVIEW.toString())) {
            tokenCount += extraTokensPerName();
            tokenCount += estimateTokenCountInText(userMessage.name());
        }

        return tokenCount;
    }

    private int estimateTokenCountIn(AiMessage aiMessage) {
        int tokenCount = 0;

        if (aiMessage.text() != null) {
            tokenCount += estimateTokenCountInText(aiMessage.text());
        }

        if (aiMessage.hasToolExecutionRequests()) {
            if (isOneOfLatestModels()) {
                tokenCount += 6;
            } else {
                tokenCount += 3;
            }
            if (aiMessage.toolExecutionRequests().size() == 1) {
                tokenCount -= 1;
                ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
                tokenCount += estimateTokenCountInText(toolExecutionRequest.name()) * 2;
                tokenCount += estimateTokenCountInText(toolExecutionRequest.arguments());
            } else {
                tokenCount += 15;
                for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                    tokenCount += 7;
                    tokenCount += estimateTokenCountInText(toolExecutionRequest.name());

                    if (isNullOrBlank(toolExecutionRequest.arguments())) {
                        continue;
                    }

                    try {
                        Map<?, ?> arguments = OBJECT_MAPPER.readValue(toolExecutionRequest.arguments(), Map.class);
                        for (Map.Entry<?, ?> argument : arguments.entrySet()) {
                            tokenCount += 2;
                            tokenCount += estimateTokenCountInText(String.valueOf(argument.getKey()));
                            tokenCount += estimateTokenCountInText(String.valueOf(argument.getValue()));
                        }
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        return tokenCount;
    }

    private int estimateTokenCountIn(ToolExecutionResultMessage toolExecutionResultMessage) {
        return estimateTokenCountInText(toolExecutionResultMessage.text());
    }

    private int extraTokensPerMessage() {
        if (modelName.equals(GPT_3_5_TURBO_0301.modelName())) {
            return 4;
        } else {
            return 3;
        }
    }

    private int extraTokensPerName() {
        if (modelName.equals(GPT_3_5_TURBO_0301.toString())) {
            return -1; // if there's a name, the role is omitted
        } else {
            return 1;
        }
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        // see https://github.com/openai/openai-cookbook/blob/main/examples/How_to_count_tokens_with_tiktoken.ipynb

        int tokenCount = 3; // every reply is primed with <|start|>assistant<|message|>
        for (ChatMessage message : messages) {
            tokenCount += estimateTokenCountInMessage(message);
        }
        return tokenCount;
    }

    private Supplier<IllegalArgumentException> unknownModelException() {
        return () -> illegalArgument("Model '%s' is unknown to jtokkit", modelName);
    }

    private boolean isOneOfLatestModels() {
        return isOneOfLatestGpt3Models() || isOneOfLatestGpt4Models();
    }

    private boolean isOneOfLatestGpt3Models() {
        return modelName.equals(GPT_3_5_TURBO_1106.toString())
                || modelName.equals(GPT_3_5_TURBO.toString());
    }

    private boolean isOneOfLatestGpt4Models() {
        return modelName.equals(GPT_4_TURBO.toString())
                || modelName.equals(GPT_4_1106_PREVIEW.toString())
                || modelName.equals(GPT_4_0125_PREVIEW.toString());
    }
}
