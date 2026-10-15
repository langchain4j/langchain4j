package dev.langchain4j.model.openai;

import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.openai.internal.shared.OpenAiError;

/**
 * Passed to {@code onError} when an OpenAI-compatible Chat Completions stream reports a failure as a
 * data chunk carrying an {@code error} object (HTTP 200 already sent), for example vLLM/LiteLLM's bare
 * {@code {"error":...}} chunk or OpenRouter's {@code finish_reason: "error"} chunk.
 *
 * @see <a href="https://github.com/openai/openai-python/blob/main/src/openai/_streaming.py">openai-python streaming</a>
 */
public class OpenAiStreamingException extends LangChain4jException {

    private final String type;
    private final String code;
    private final String param;

    public OpenAiStreamingException(OpenAiError error) {
        super(error == null || error.message() == null ? "OpenAI streaming error" : error.message());
        this.type = error == null ? null : error.type();
        this.code = error == null ? null : error.code();
        this.param = error == null ? null : error.param();
    }

    public OpenAiStreamingException(String message, String type, String code, String param) {
        super(message);
        this.type = type;
        this.code = code;
        this.param = param;
    }

    public String type() {
        return type;
    }

    public String code() {
        return code;
    }

    public String param() {
        return param;
    }
}
