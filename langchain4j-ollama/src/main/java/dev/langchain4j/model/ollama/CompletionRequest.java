package dev.langchain4j.model.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class CompletionRequest {

    private String model;
    private String system;
    private String prompt;
    private Options options;
    private String format;
    private Boolean stream;
    /**
     * By default, models are retained in memory for 5 minutes to expedite response times for frequent requests. However, you might prefer to release the memory sooner or retain the model indefinitely. This is managed through the `keep_alive` parameter available in the `/api/generate` and `/api/chat` API endpoints.
     *
     * The `keep_alive` parameter accepts:
     * - a duration string (e.g., "10m" or "24h")
     * - a number in seconds (e.g., 3600)
     * - any negative number to keep the model loaded indefinitely (e.g., -1 or "-1m")
     * - '0' to unload the model immediately after a response is generated
     *
     * For instance, to preload a model and keep it in memory, you can use:
     * curl http://localhost:11434/api/generate -d '{"model": "llama3", "keep_alive": -1}'
     * To unload a model immediately and free up memory:
     * curl http://localhost:11434/api/generate -d '{"model": "llama3", "keep_alive": 0}'
     *
     * You can also globally set the duration for which all models are kept in memory by configuring the `OLLAMA_KEEP_ALIVE` environment variable when launching the Ollama server. This variable accepts the same types of parameters as the `keep_alive` parameter.
     *
     * To override the global `OLLAMA_KEEP_ALIVE` setting, specify the `keep_alive` parameter directly in the `/api/generate` or `/api/chat` API requests.
     *
     * Extracted from Ollama FAQ documentation:
     * https://github.com/ollama/ollama/blob/main/docs/faq.md#how-can-i-pre-load-a-model-to-get-faster-response-times
     * 
     */
    private String keepAlive;
}
