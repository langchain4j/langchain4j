package dev.langchain4j.service;

import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;
import lombok.Builder;

import java.util.List;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents the result of an AI Service invocation.
 * It contains actual content (LLM response) and additional information associated with it,
 * such as {@link TokenUsage} and sources ({@link Content}s retrieved during RAG).
 *
 * @param <T> The type of the content.
 */
public class Result<T> {

    private final T content;
    private final TokenUsage tokenUsage;
    private final List<Content> sources;

    @Builder
    public Result(T content, TokenUsage tokenUsage, List<Content> sources) {
        this.content = ensureNotNull(content, "content");
        this.tokenUsage = ensureNotNull(tokenUsage, "tokenUsage");
        this.sources = copyIfNotNull(sources);
    }

    public T content() {
        return content;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    public List<Content> sources() {
        return sources;
    }
}
