package dev.langchain4j.model.scoring.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.scoring.ScoringModel;

/**
 * A listener of a {@link ScoringModel}: it is notified before each scoring call, after each successful call and
 * when a call fails. Use it, for example, to log what was scored, or to record metrics and traces such as the
 * latency, the number of scored documents and the token usage.
 * <p>
 * Listeners are configured on the builder of a scoring model, for example:
 * <pre>{@code
 * ScoringModel scoringModel = CohereScoringModel.builder()
 *         .apiKey(System.getenv("COHERE_API_KEY"))
 *         .modelName("rerank-v3.5")
 *         .listeners(List.of(new MyScoringModelListener()))
 *         .build();
 * }</pre>
 * <p>
 * All methods are called on the thread that calls the model (or, for asynchronous calls, on the thread that completes
 * the call, which can be an I/O thread: do not block in listeners). Exceptions thrown by a listener are logged and
 * ignored, so a failing listener never breaks the scoring call.
 *
 * @since 1.23.0
 */
@Experimental
public interface ScoringModelListener {

    /**
     * Called before the request is sent to the model.
     *
     * @param requestContext the request, the model provider and the attributes. The attributes can be used to pass
     *                       data between the methods of this listener, or between multiple listeners.
     */
    default void onRequest(ScoringModelRequestContext requestContext) {}

    /**
     * Called after the scores are received from the model.
     *
     * @param responseContext the response, the corresponding request, the model provider and the attributes.
     */
    default void onResponse(ScoringModelResponseContext responseContext) {}

    /**
     * Called when an error occurs during the interaction with the model.
     *
     * @param errorContext the error, the corresponding request, the model provider and the attributes.
     */
    default void onError(ScoringModelErrorContext errorContext) {}
}
