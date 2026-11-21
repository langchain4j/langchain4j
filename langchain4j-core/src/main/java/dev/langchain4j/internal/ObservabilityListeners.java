package dev.langchain4j.internal;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.spi.observability.ObservabilityListenersProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds the listeners of the {@link ObservabilityListenersProvider}s found with {@link ServiceLoader} to AI Services
 * and to model calls. The providers are looked up once, in the class loader of LangChain4j. A framework that loads the
 * application with class loaders of its own (for example Quarkus) finds the providers of those class loaders with
 * {@link #load(ClassLoader)} and passes them to {@link #useFrameworkProviders(List)}. Only providers of LangChain4j itself (classes in
 * {@code dev.langchain4j} packages) are used: they receive the content of every model call, so a provider of another
 * library on the classpath is logged and skipped. A provider that cannot be loaded, or whose listeners cannot be
 * obtained, is logged and skipped: it never breaks an AI Service or a model call.
 */
@Internal
public final class ObservabilityListeners {

    private static final Logger LOG = LoggerFactory.getLogger(ObservabilityListeners.class);

    private static final int MAX_LOAD_FAILURES = 100;

    private static final String ACCEPTED_PACKAGE_PREFIX = "dev.langchain4j.";

    private static final Set<AiServiceListenerRegistrar> REGISTRARS =
            Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

    private ObservabilityListeners() {}

    /**
     * Registers the provided AI Service listeners with the given registrar, once per registrar, so that a registrar
     * shared by several AI Services does not receive the same listener several times.
     */
    public static void registerWith(AiServiceListenerRegistrar registrar) {
        List<AiServiceListener<?>> listeners = provided(ObservabilityListenersProvider::aiServiceListeners);
        if (listeners.isEmpty() || !REGISTRARS.add(registrar)) {
            return;
        }
        try {
            registrar.register(listeners);
        } catch (RuntimeException e) {
            REGISTRARS.remove(registrar);
            LOG.warn("Failed to register the provided AI Service listeners with {}", registrar.getClass(), e);
        }
    }

    public static List<ChatModelListener> chat(List<ChatModelListener> listeners) {
        return withProvided(listeners, ObservabilityListenersProvider::chatModelListeners);
    }

    public static List<EmbeddingModelListener> embedding(List<EmbeddingModelListener> listeners) {
        return withProvided(listeners, ObservabilityListenersProvider::embeddingModelListeners);
    }

    public static List<ModerationModelListener> moderation(List<ModerationModelListener> listeners) {
        return withProvided(listeners, ObservabilityListenersProvider::moderationModelListeners);
    }

    public static List<ScoringModelListener> scoring(List<ScoringModelListener> listeners) {
        return withProvided(listeners, ObservabilityListenersProvider::scoringModelListeners);
    }

    public static List<DecisionModelListener> decision(List<DecisionModelListener> listeners) {
        return withProvided(listeners, ObservabilityListenersProvider::decisionModelListeners);
    }

    private static <T> List<T> withProvided(
            List<T> listeners, Function<ObservabilityListenersProvider, List<? extends T>> getter) {
        List<T> provided = provided(getter);
        if (provided.isEmpty()) {
            return listeners;
        }
        List<T> all = new ArrayList<>(listeners == null ? List.of() : listeners);
        all.addAll(provided);
        return all;
    }

    private static <T> List<T> provided(Function<ObservabilityListenersProvider, List<? extends T>> getter) {
        List<ObservabilityListenersProvider> providers = providers();
        if (providers.isEmpty()) {
            return List.of();
        }
        List<T> listeners = new ArrayList<>();
        for (ObservabilityListenersProvider provider : providers) {
            try {
                List<? extends T> providedListeners = getter.apply(provider);
                if (providedListeners != null) {
                    listeners.addAll(providedListeners);
                }
            } catch (RuntimeException | LinkageError e) {
                LOG.warn("Failed to get the listeners of {}", provider.getClass().getName(), e);
            }
        }
        return listeners;
    }

    private static volatile List<ObservabilityListenersProvider> ownAndFrameworkProviders;

    /**
     * Uses the given providers, found by a framework in the class loaders of the application (see
     * {@link #load(ClassLoader)}), in addition to the providers of LangChain4j's own class loader. They replace the
     * providers given before: a framework calls this method each time it starts the application, and with an empty
     * list when it stops it. A provider class found in both is used once, from the framework's providers.
     */
    public static void useFrameworkProviders(List<ObservabilityListenersProvider> frameworkProviders) {
        ensureNotNull(frameworkProviders, "frameworkProviders");
        if (frameworkProviders.isEmpty()) {
            ownAndFrameworkProviders = null;
            return;
        }
        List<ObservabilityListenersProvider> all = new ArrayList<>(frameworkProviders);
        Set<String> classNames = new HashSet<>();
        frameworkProviders.forEach(provider -> classNames.add(provider.getClass().getName()));
        OwnProviders.PROVIDERS.stream()
                .filter(provider -> !classNames.contains(provider.getClass().getName()))
                .forEach(all::add);
        ownAndFrameworkProviders = List.copyOf(all);
    }

    private static List<ObservabilityListenersProvider> providers() {
        List<ObservabilityListenersProvider> all = ownAndFrameworkProviders;
        return all == null ? OwnProviders.PROVIDERS : all;
    }

    private static final class OwnProviders {

        private static final List<ObservabilityListenersProvider> PROVIDERS =
                load(ObservabilityListeners.class.getClassLoader());
    }

    /**
     * Loads the providers found with {@link ServiceLoader} in the given class loader, each time it is called. Only
     * providers of LangChain4j are returned; a provider that cannot be loaded is logged and skipped.
     */
    public static List<ObservabilityListenersProvider> load(ClassLoader classLoader) {
        List<ObservabilityListenersProvider> providers = new ArrayList<>();
        Iterator<ServiceLoader.Provider<ObservabilityListenersProvider>> iterator = ServiceLoader.load(
                        ObservabilityListenersProvider.class, classLoader)
                .stream()
                .iterator();
        for (int failures = 0; failures < MAX_LOAD_FAILURES; ) {
            try {
                if (!iterator.hasNext()) {
                    break;
                }
                ServiceLoader.Provider<ObservabilityListenersProvider> provider = iterator.next();
                String className = provider.type().getName();
                if (className.startsWith(ACCEPTED_PACKAGE_PREFIX)) {
                    providers.add(provider.get());
                } else {
                    LOG.warn(
                            "Ignoring the ObservabilityListenersProvider {}: only the providers of LangChain4j are used",
                            className);
                }
            } catch (ServiceConfigurationError | LinkageError e) {
                failures++;
                LOG.warn("Ignoring an ObservabilityListenersProvider that cannot be loaded", e);
            }
        }
        if (!providers.isEmpty()) {
            LOG.info(
                    "Using the ObservabilityListenersProviders {}: they receive the content of every model call",
                    providers.stream().map(provider -> provider.getClass().getName()).toList());
        }
        return List.copyOf(providers);
    }
}
