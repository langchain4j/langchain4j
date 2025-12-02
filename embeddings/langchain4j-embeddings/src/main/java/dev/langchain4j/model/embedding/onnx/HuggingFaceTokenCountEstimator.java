package dev.langchain4j.model.embedding.onnx;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.TokenCountEstimator;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.Files.newInputStream;

/**
 * A token count estimator for models that can be found on <a href="https://huggingface.co/">HuggingFace</a>.
 * <br>
 * Uses DJL's {@link HuggingFaceTokenizer} under the hood.
 * <br>
 * Requires {@code tokenizer.json} to instantiate.
 * An <a href="https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/blob/main/tokenizer.json">example</a>.
 */
public class HuggingFaceTokenCountEstimator implements TokenCountEstimator {

    private final HuggingFaceTokenizer tokenizer;

    /**
     * Creates an instance of a {@code HuggingFaceTokenCountEstimator} using a built-in {@code tokenizer.json} file.
     */
    public HuggingFaceTokenCountEstimator() {

        Map<String, String> options = new HashMap<>();
        options.put("padding", "false");
        options.put("truncation", "false");

        this.tokenizer = createFrom(getClass().getResourceAsStream("/bert-tokenizer.json"), options);
    }

    /**
     * Creates an instance of a {@code HuggingFaceTokenCountEstimator} using a provided {@code tokenizer.json} file.
     *
     * @param pathToTokenizer The path to the tokenizer file (e.g., "/path/to/tokenizer.json")
     */
    public HuggingFaceTokenCountEstimator(Path pathToTokenizer) {
        this(pathToTokenizer, null);
    }

    /**
     * Creates an instance of a {@code HuggingFaceTokenCountEstimator} using a provided {@code tokenizer.json} file
     * and a map of DJL's tokenizer options.
     *
     * @param pathToTokenizer The path to the tokenizer file (e.g., "/path/to/tokenizer.json")
     * @param options         The DJL's tokenizer options
     */
    public HuggingFaceTokenCountEstimator(Path pathToTokenizer, Map<String, String> options) {
        try {
            this.tokenizer = createFrom(newInputStream(pathToTokenizer), options);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates an instance of a {@code HuggingFaceTokenCountEstimator} using a provided {@code tokenizer.json} file.
     *
     * @param pathToTokenizer The path to the tokenizer file (e.g., "/path/to/tokenizer.json")
     */
    public HuggingFaceTokenCountEstimator(String pathToTokenizer) {
        this(pathToTokenizer, null);
    }

    /**
     * Creates an instance of a {@code HuggingFaceTokenCountEstimator} using a provided {@code tokenizer.json} file
     * and a map of DJL's tokenizer options.
     *
     * @param pathToTokenizer The path to the tokenizer file (e.g., "/path/to/tokenizer.json")
     * @param options         The DJL's tokenizer options
     */
    public HuggingFaceTokenCountEstimator(String pathToTokenizer, Map<String, String> options) {
        try {
            this.tokenizer = createFrom(newInputStream(Paths.get(pathToTokenizer)), options);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static HuggingFaceTokenizer createFrom(InputStream tokenizer,
                                                   Map<String, String> options) {
        try {
            return HuggingFaceTokenizer.newInstance(tokenizer, options);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int estimateTokenCountInText(String text) {
        Encoding encoding = tokenizer.encode(text, false, true);
        return encoding.getTokens().length;
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        if (message instanceof SystemMessage systemMessage) {
            return estimateTokenCountInText(systemMessage.text());
        } else if (message instanceof UserMessage userMessage) {
            return estimateTokenCountInText(userMessage.singleText());
        } else if (message instanceof AiMessage aiMessage) {
            return aiMessage.text() == null ? 0 : estimateTokenCountInText(aiMessage.text());
        } else if (message instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            return estimateTokenCountInText(toolExecutionResultMessage.text());
        } else {
            throw new IllegalArgumentException("Unknown message type: " + message);
        }
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        int tokens = 0;
        for (ChatMessage message : messages) {
            tokens += estimateTokenCountInMessage(message);
        }
        return tokens;
    }
}
