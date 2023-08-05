package dev.langchain4j.model.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static java.util.stream.Collectors.toList;

public abstract class AbstractInProcessEmbeddingModel implements EmbeddingModel, TokenCountEstimator {

    private static final int BERT_MAX_TOKENS = 510; // 512 - 2 (special tokens [CLS] and [SEP])

    static OnnxBertEmbeddingModel load(String pathToModel) {
        try {
            return new OnnxBertEmbeddingModel(Files.newInputStream(Paths.get(pathToModel)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract OnnxBertEmbeddingModel model();

    @Override
    public List<Embedding> embedAll(List<TextSegment> segments) {
        return segments.stream()
                .map(segment -> {
                    String text = segment.text();
                    int tokenCount = estimateTokenCount(text);
                    if (tokenCount > BERT_MAX_TOKENS) {
                        throw illegalArgument("Cannot embed text longer than %s tokens. " +
                                "The following text is %s tokens long: %s", BERT_MAX_TOKENS, tokenCount, text);
                    }
                    float[] vector = model().embed(text);
                    return Embedding.from(vector);
                })
                .collect(toList());
    }

    @Override
    public int estimateTokenCount(String text) {
        return model().countTokens(text);
    }
}
