package dev.langchain4j.model.openai;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.Tokenizer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.roleFrom;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO_0301;

public class OpenAiTokenizer implements Tokenizer {

    private final String modelName;
    private final Optional<Encoding> encoding;

    public OpenAiTokenizer(String modelName) {
        this.modelName = modelName;
        // If the model is unknown, we should NOT fail fast during the creation of OpenAiTokenizer.
        // Doing so would cause the failure of every OpenAI***Model that uses this tokenizer.
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
        int tokenCount = 0;
        tokenCount += extraTokensPerMessage();
        tokenCount += estimateTokenCountInText(message.text());
        tokenCount += estimateTokenCountInText(roleFrom(message).toString());

        if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;
            if (userMessage.name() != null) {
                tokenCount += extraTokensPerName();
                tokenCount += estimateTokenCountInText(userMessage.name());
            }
        }

        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            if (aiMessage.toolExecutionRequest() != null) {
                tokenCount += 4; // found experimentally while playing with OpenAI API
                ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequest();
                tokenCount += estimateTokenCountInText(toolExecutionRequest.name());
                tokenCount += estimateTokenCountInText(toolExecutionRequest.arguments());
            }
        }

        if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) message;
            tokenCount += -1; // found experimentally while playing with OpenAI API
            tokenCount += estimateTokenCountInText(toolExecutionResultMessage.toolName());
        }

        return tokenCount;
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

    @Override
    public int estimateTokenCountInToolSpecifications(Iterable<ToolSpecification> toolSpecifications) {
        int tokenCount = 0;
        for (ToolSpecification toolSpecification : toolSpecifications) {
            tokenCount += estimateTokenCountInText(toolSpecification.name());
            tokenCount += estimateTokenCountInText(toolSpecification.description());
            Map<String, Map<String, Object>> properties = toolSpecification.parameters().properties();
            for (String property : properties.keySet()) {
                for (Map.Entry<String, Object> entry : properties.get(property).entrySet()) {
                    if ("type".equals(entry.getKey())) {
                        tokenCount += 3; // found experimentally while playing with OpenAI API
                        tokenCount += estimateTokenCountInText(entry.getValue().toString());
                    } else if ("description".equals(entry.getKey())) {
                        tokenCount += 3; // found experimentally while playing with OpenAI API
                        tokenCount += estimateTokenCountInText(entry.getValue().toString());
                    } else if ("enum".equals(entry.getKey())) {
                        tokenCount -= 3; // found experimentally while playing with OpenAI API
                        for (Object enumValue : (Object[]) entry.getValue()) {
                            tokenCount += 3; // found experimentally while playing with OpenAI API
                            tokenCount += estimateTokenCountInText(enumValue.toString());
                        }
                    }
                }
            }
            tokenCount += 12; // found experimentally while playing with OpenAI API
        }
        tokenCount += 12; // found experimentally while playing with OpenAI API
        return tokenCount;
    }

    private int extraTokensPerMessage() {
        if (modelName.equals(GPT_3_5_TURBO_0301)) {
            return 4;
        } else {
            return 3;
        }
    }

    private int extraTokensPerName() {
        if (modelName.equals(GPT_3_5_TURBO_0301)) {
            return -1; // if there's a name, the role is omitted
        } else {
            return 1;
        }
    }

    public List<Integer> encode(String text) {
        return encoding.orElseThrow(unknownModelException())
                .encodeOrdinary(text);
    }

    public List<Integer> encode(String text, int maxTokensToEncode) {
        return encoding.orElseThrow(unknownModelException())
                .encodeOrdinary(text, maxTokensToEncode).getTokens();
    }

    public String decode(List<Integer> tokens) {
        return encoding.orElseThrow(unknownModelException())
                .decode(tokens);
    }

    private Supplier<IllegalArgumentException> unknownModelException() {
        return () -> illegalArgument("Model '%s' is unknown to jtokkit", modelName);
    }
}
