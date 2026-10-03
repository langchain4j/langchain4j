package dev.langchain4j.spi;

/**
 * Decides which implementation wins when more than one is registered for the same service.
 *
 * <p>{@link ServiceHelper#loadFactory} selects the first implementation. Callers that use all
 * implementations can load them with {@link ServiceHelper#loadAllFactories}; their collection is
 * ordered by the same priorities.
 *
 * <p>Higher wins. A factory that does not implement this interface is {@link #DEFAULT_PRIORITY},
 * so an application or framework that supplies its own implementation keeps it: a factory has to
 * ask to lose. Among equal priorities the {@code ServiceLoader} order is preserved, and
 * {@code ServiceHelper.loadFactory} logs a warning naming the selected implementation when more
 * than one is available.
 */
public interface PrioritizedFactory {

    /**
     * The priority of anything that does not implement this interface.
     */
    int DEFAULT_PRIORITY = 0;

    /**
     * A priority for an implementation that should apply only when nothing else is registered for
     * the service - the opt-in JSON codecs use this, so that adding them to an application whose
     * framework already supplies a codec does not silently take that framework's behaviour away.
     */
    int YIELDS_TO_OTHERS = -100;

    int priority();
}
