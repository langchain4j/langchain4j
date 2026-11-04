package dev.langchain4j.spi.observability;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Provides listeners that are added to every AI Service and to every model call, in addition to the listeners
 * configured by the user. Implementations are found with {@link ServiceLoader}, once per class loader, in the
 * context class loader of the current thread and in the class loader of LangChain4j. Only implementations in {@code dev.langchain4j} packages are used; others are logged and skipped.
 * <p>
 * This is meant for LangChain4j's own modules (for example {@code langchain4j-test}), which need to observe every
 * AI Service and model without being configured on each one. It is internal and may change at any time. Listeners
 * provided this way receive the full content of every call (prompts, responses, tool arguments and results).
 * <p>
 * Model listeners are notified by the default implementations of the model interfaces (for example
 * {@code ChatModel.chat(ChatRequest)}), so models that notify their listeners themselves do not notify them.
 */
@Internal
public interface ObservabilityListenersProvider {

    default List<AiServiceListener<?>> aiServiceListeners() {
        return List.of();
    }

    default List<ChatModelListener> chatModelListeners() {
        return List.of();
    }

    default List<EmbeddingModelListener> embeddingModelListeners() {
        return List.of();
    }

    default List<ModerationModelListener> moderationModelListeners() {
        return List.of();
    }

    default List<ScoringModelListener> scoringModelListeners() {
        return List.of();
    }

    default List<DecisionModelListener> decisionModelListeners() {
        return List.of();
    }
}
