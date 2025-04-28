package dev.langchain4j.model.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.IntArrayList;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.knuddels.jtokkit.api.EncodingType.O200K_BASE;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * This class can be used to estimate the cost (in tokens) before calling OpenAI.
 * Magic numbers present in this class were found empirically while testing.
 * There are integration tests in place that are making sure that the calculations here are very close to that of OpenAI.
 */
public class OpenAiTokenCountEstimator implements TokenCountEstimator {

    private static final EncodingRegistry ENCODING_REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String modelName;
    private final Encoding encoding;

    /**
     * Creates an instance of the {@code OpenAiTokenCountEstimator} for a given {@link OpenAiChatModelName}.
     */
    public OpenAiTokenCountEstimator(OpenAiChatModelName modelName) {
        this(modelName.toString());
    }

    /**
     * Creates an instance of the {@code OpenAiTokenCountEstimator} for a given {@link OpenAiEmbeddingModelName}.
     */
    public OpenAiTokenCountEstimator(OpenAiEmbeddingModelName modelName) {
        this(modelName.toString());
    }

    /**
     * Creates an instance of the {@code OpenAiTokenCountEstimator} for a given {@link OpenAiLanguageModelName}.
     */
    public OpenAiTokenCountEstimator(OpenAiLanguageModelName modelName) {
        this(modelName.toString());
    }

    /**
     * Creates an instance of the {@code OpenAiTokenCountEstimator} for a given model name.
     */
    public OpenAiTokenCountEstimator(String modelName) {
        this.modelName = ensureNotBlank(modelName, "modelName");
        if (modelName.startsWith("o") || modelName.startsWith("gpt-4.")) {
            // temporary fix until https://github.com/knuddelsgmbh/jtokkit/pull/118 is released
            this.encoding = ENCODING_REGISTRY.getEncoding(O200K_BASE);
        } else {
            this.encoding = ENCODING_REGISTRY.getEncodingForModel(modelName).orElseThrow(unknownModelException());
        }
    }

    public int estimateTokenCountInText(String text) {
        return encoding.countTokensOrdinary(text);
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        int tokenCount = 1; // 1 token for role
        tokenCount += 3; // extra tokens per each message

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
            } else {
                throw illegalArgument("Unknown content type: " + content);
            }
        }

        if (userMessage.name() != null) {
            tokenCount += 1; // extra tokens per name
            tokenCount += estimateTokenCountInText(userMessage.name());
        }

        return tokenCount;
    }

    private int estimateTokenCountIn(AiMessage aiMessage) {
        int tokenCount = 0;

        if (aiMessage.text() != null) {
            tokenCount += estimateTokenCountInText(aiMessage.text());
        }

        if (aiMessage.toolExecutionRequests() != null) {
            tokenCount += 6;
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

                    Map<?, ?> arguments;
                    try {
                        arguments = OBJECT_MAPPER.readValue(toolExecutionRequest.arguments(), Map.class);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    for (Map.Entry<?, ?> argument : arguments.entrySet()) {
                        tokenCount += 2;
                        tokenCount += estimateTokenCountInText(argument.getKey().toString());
                        tokenCount += estimateTokenCountInText(argument.getValue().toString());
                    }
                }
            }
        }

        if (modelName.startsWith("o4")) {
            tokenCount += 2;
        }

        return tokenCount;
    }

    private int estimateTokenCountIn(ToolExecutionResultMessage toolExecutionResultMessage) {
        return estimateTokenCountInText(toolExecutionResultMessage.text());
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        // see https://github.com/openai/openai-cookbook/blob/main/examples/How_to_count_tokens_with_tiktoken.ipynb

        int tokenCount = 3; // every reply is primed with <|start|>assistant<|message|>
        for (ChatMessage message : messages) {
            tokenCount += estimateTokenCountInMessage(message);
        }
        if (modelName.startsWith("o") ) {
            tokenCount -= 1;
        }
        return tokenCount;
    }

    public List<Integer> encode(String text) {
        return encoding.encodeOrdinary(text).boxed();
    }

    public List<Integer> encode(String text, int maxTokensToEncode) {
        return encoding.encodeOrdinary(text, maxTokensToEncode).getTokens().boxed();
    }

    public String decode(List<Integer> tokens) {

        IntArrayList intArrayList = new IntArrayList();
        for (Integer token : tokens) {
            intArrayList.add(token);
        }

        return encoding.decode(intArrayList);
    }

    private Supplier<IllegalArgumentException> unknownModelException() {
        return () -> illegalArgument("Model '%s' is unknown to jtokkit", modelName);
    }
}
