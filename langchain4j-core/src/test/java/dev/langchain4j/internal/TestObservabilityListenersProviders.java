package dev.langchain4j.internal;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import dev.langchain4j.model.moderation.listener.ModerationModelRequestContext;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.model.scoring.listener.ScoringModelRequestContext;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.observability.api.listener.AiServiceStartedListener;
import dev.langchain4j.spi.observability.ObservabilityListenersProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * {@link ObservabilityListenersProvider}s used by {@link ObservabilityListenersTest}. {@link Switchable} is registered
 * with {@link ServiceLoader} in the test resources, so LangChain4j finds it in its own class loader; it provides the
 * listeners of the {@link #active} providers, none outside of {@link ObservabilityListenersTest}. The other providers
 * are registered by {@link ObservabilityListenersTest} through a class loader of its own.
 */
public final class TestObservabilityListenersProviders {

    static final List<Object> RECEIVED = new CopyOnWriteArrayList<>();

    static volatile List<ObservabilityListenersProvider> active = List.of();

    private TestObservabilityListenersProviders() {}

    public static class Switchable implements ObservabilityListenersProvider {

        @Override
        public List<AiServiceListener<?>> aiServiceListeners() {
            return collect(ObservabilityListenersProvider::aiServiceListeners);
        }

        @Override
        public List<ChatModelListener> chatModelListeners() {
            return collect(ObservabilityListenersProvider::chatModelListeners);
        }

        @Override
        public List<EmbeddingModelListener> embeddingModelListeners() {
            return collect(ObservabilityListenersProvider::embeddingModelListeners);
        }

        @Override
        public List<ModerationModelListener> moderationModelListeners() {
            return collect(ObservabilityListenersProvider::moderationModelListeners);
        }

        @Override
        public List<ScoringModelListener> scoringModelListeners() {
            return collect(ObservabilityListenersProvider::scoringModelListeners);
        }

        @Override
        public List<DecisionModelListener> decisionModelListeners() {
            return collect(ObservabilityListenersProvider::decisionModelListeners);
        }

        private static <T> List<T> collect(Function<ObservabilityListenersProvider, List<? extends T>> getter) {
            List<T> listeners = new ArrayList<>();
            active.forEach(provider -> listeners.addAll(getter.apply(provider)));
            return listeners;
        }
    }

    public static class Recording implements ObservabilityListenersProvider {

        @Override
        public List<AiServiceListener<?>> aiServiceListeners() {
            return List.of((AiServiceStartedListener) RECEIVED::add);
        }

        @Override
        public List<ChatModelListener> chatModelListeners() {
            return List.of(new ChatModelListener() {
                @Override
                public void onRequest(ChatModelRequestContext requestContext) {
                    RECEIVED.add(requestContext);
                }
            });
        }

        @Override
        public List<ScoringModelListener> scoringModelListeners() {
            return List.of(new ScoringModelListener() {
                @Override
                public void onRequest(ScoringModelRequestContext requestContext) {
                    RECEIVED.add(requestContext);
                }
            });
        }

        @Override
        public List<EmbeddingModelListener> embeddingModelListeners() {
            return List.of(new EmbeddingModelListener() {
                @Override
                public void onRequest(EmbeddingModelRequestContext requestContext) {
                    RECEIVED.add(requestContext);
                }
            });
        }

        @Override
        public List<ModerationModelListener> moderationModelListeners() {
            return List.of(new ModerationModelListener() {
                @Override
                public void onRequest(ModerationModelRequestContext requestContext) {
                    RECEIVED.add(requestContext);
                }
            });
        }

        @Override
        public List<DecisionModelListener> decisionModelListeners() {
            return List.of(new DecisionModelListener() {
                @Override
                public void onRequest(DecisionModelRequestContext requestContext) {
                    RECEIVED.add(requestContext);
                }
            });
        }
    }

    public static class ThrowingFromGetter implements ObservabilityListenersProvider {

        @Override
        public List<AiServiceListener<?>> aiServiceListeners() {
            throw new IllegalStateException("cannot provide listeners");
        }

        @Override
        public List<ChatModelListener> chatModelListeners() {
            throw new IllegalStateException("cannot provide listeners");
        }
    }

    public static class WithoutNoArgConstructor implements ObservabilityListenersProvider {

        public WithoutNoArgConstructor(String name) {}
    }

    public static class ThrowingFromConstructor implements ObservabilityListenersProvider {

        public ThrowingFromConstructor() {
            throw new IllegalStateException("cannot be created");
        }
    }

    public static class ThrowingFromStaticInitializer implements ObservabilityListenersProvider {

        static {
            if (true) {
                throw new IllegalStateException("cannot be initialized");
            }
        }
    }
}
