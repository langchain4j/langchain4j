package dev.langchain4j.spi;

/**
 * Decides which implementation wins when more than one is registered for a service of which only one
 * is used, such as a JSON codec or a prompt template factory.
 *
 * <p>Without this, the winner is whatever the {@link java.util.ServiceLoader} happened to enumerate
 * first - which can differ between a development run, a shaded jar and a container image.
 *
 * <p>Higher wins. A factory that does not implement this interface is {@link #DEFAULT_PRIORITY},
 * so an application or framework that supplies its own implementation keeps it: a factory has to
 * ask to lose. Among equal priorities the {@code ServiceLoader} order is preserved, and
 * {@code ServiceHelper} logs a warning naming the winner.
 *
 * <p>For a service of which every implementation is used, priority decides the order in which they
 * are returned.
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
