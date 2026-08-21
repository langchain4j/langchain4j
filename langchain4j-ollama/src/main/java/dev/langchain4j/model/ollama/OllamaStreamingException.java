package dev.langchain4j.model.ollama;

import dev.langchain4j.exception.LangChain4jException;

/**
 * Exception thrown when Ollama returns an error during streaming.
 *
 * @see <a href="https://docs.ollama.com/api/errors#errors-that-occur-while-streaming">Ollama streaming errors</a>
 */
public class OllamaStreamingException extends LangChain4jException {

    public OllamaStreamingException(String message) {
        super(message);
    }
}
