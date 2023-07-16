package dev.langchain4j.model.openai;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.Tokenizer;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.model.openai.OpenAiHelper.roleFrom;

public class OpenAiTokenizer implements Tokenizer {

    private final String modelName;
    private final Optional<Encoding> encoding;

    public OpenAiTokenizer(String modelName) {
        this.modelName = modelName;
        this.encoding = Encodings.newDefaultEncodingRegistry().getEncodingForModel(modelName);
    }

    public int countTokens(String text) {
        return encoding.orElseThrow(unknownModelException())
                .countTokensOrdinary(text);
    }

    @Override
    public int countTokens(ChatMessage message) {
        return extraTokensPerEachMessage()
                + countTokens(message.text())
                + countTokens(roleFrom(message).toString());
    }

    @Override
    public int countTokens(Iterable<ChatMessage> messages) {
        // see https://jtokkit.knuddels.de/docs/getting-started/recipes/chatml

        int tokenCount = 3;
        for (ChatMessage message : messages) {
            tokenCount += countTokens(message);
        }
        return tokenCount;
    }

    private int extraTokensPerEachMessage() {
        if (modelName.startsWith(OpenAiModelName.GPT_4)) {
            return 3;
        } else {
            return 4;
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
