package dev.langchain4j.service;

import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.tool.ToolExecution;
import java.util.List;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents the result of an AI Service invocation.
 * It contains actual content (LLM response) and additional information associated with it,
 * such as {@link TokenUsage} and sources ({@link Content}s retrieved during RAG).
 *
 * @param <T> The type of the content. Can be of any return type supported by AI Services,
 *            such as String, Enum, MyCustomPojo, etc.
 */
public class Result<T> {

    private final T content;
    private final TokenUsage tokenUsage;
    private final List<Content> sources;
    private final FinishReason finishReason;
    private final List<ToolExecution> toolExecutions;

    public Result(T content,
                  TokenUsage tokenUsage,
                  List<Content> sources,
                  FinishReason finishReason,
                  List<ToolExecution> toolExecutions) {
        this.content = ensureNotNull(content, "content");
        this.tokenUsage = tokenUsage;
        this.sources = copy(sources);
        this.finishReason = finishReason;
        this.toolExecutions = copy(toolExecutions);
    }

    public static <T> ResultBuilder<T> builder() {
        return new ResultBuilder<T>();
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

    public FinishReason finishReason() {
        return finishReason;
    }

    public List<ToolExecution> toolExecutions() {
        return toolExecutions;
    }

    public static class ResultBuilder<T> {

        private T content;
        private TokenUsage tokenUsage;
        private List<Content> sources;
        private FinishReason finishReason;
        private List<ToolExecution> toolExecutions;

        ResultBuilder() {
        }

        public ResultBuilder<T> content(T content) {
            this.content = content;
            return this;
        }

        public ResultBuilder<T> tokenUsage(TokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return this;
        }

        public ResultBuilder<T> sources(List<Content> sources) {
            this.sources = sources;
            return this;
        }

        public ResultBuilder<T> finishReason(FinishReason finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        public ResultBuilder<T> toolExecutions(List<ToolExecution> toolExecutions) {
            this.toolExecutions = toolExecutions;
            return this;
        }

        public Result<T> build() {
            return new Result<T>(this.content, this.tokenUsage, this.sources, this.finishReason, this.toolExecutions);
        }
    }
}
