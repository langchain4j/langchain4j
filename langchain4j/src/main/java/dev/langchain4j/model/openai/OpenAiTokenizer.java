package dev.langchain4j.model.openai;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.Tokenizer;

import java.util.List;
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

    public int countTokens(String text) {
        return encoding.orElseThrow(unknownModelException())
                .countTokensOrdinary(text);
    }

    @Override
    public int countTokens(ChatMessage message) {
        int tokenCount = 0;
        tokenCount += extraTokensPerMessage();
        tokenCount += countTokens(message.text());
        tokenCount += countTokens(roleFrom(message).toString());

        if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;
            if (userMessage.name() != null) {
                tokenCount += extraTokensPerName();
                tokenCount += countTokens(userMessage.name());
            }
        }

        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            if (aiMessage.toolExecutionRequest() != null) {
                tokenCount += 4; // found experimentally while playing with OpenAI API
                ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequest();
                tokenCount += countTokens(toolExecutionRequest.name());
                tokenCount += countTokens(toolExecutionRequest.arguments());
            }
        }

        if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) message;
            tokenCount += -1; // found experimentally while playing with OpenAI API
            tokenCount += countTokens(toolExecutionResultMessage.toolName());
        }

        return tokenCount;
    }

    @Override
    public int countTokens(Iterable<ChatMessage> messages) {
        // see https://github.com/openai/openai-cookbook/blob/main/examples/How_to_count_tokens_with_tiktoken.ipynb

        int tokenCount = 3; // every reply is primed with <|start|>assistant<|message|>
        for (ChatMessage message : messages) {
            tokenCount += countTokens(message);
        }
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
