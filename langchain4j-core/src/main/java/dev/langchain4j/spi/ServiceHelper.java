package dev.langchain4j.spi;

import dev.langchain4j.Internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Comparator;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility wrapper around {@code ServiceLoader.load()}.
 */
@Internal
public class ServiceHelper {

    private static final Logger log = LoggerFactory.getLogger(ServiceHelper.class);

    /**
     * Utility class, no public constructor.
     */
    private ServiceHelper() {
    }

    /**
     * Load the service of a given type with the highest {@link PrioritizedFactory#priority() priority}.
     *
     * <p>If several services share the highest priority, the first one found by the
     * {@link ServiceLoader} is returned and a warning is logged, because that order depends on the
     * classpath and is not stable.</p>
     *
     * @param clazz the type of service
     * @param <T>   the type of service
     * @return the service with the highest priority, null if none
     */
    public static <T> T loadFactory(Class<T> clazz) {
        List<T> factories = loadSortedByPriority(clazz, null);
        if (factories.isEmpty()) {
            return null;
        }
        String warning = ambiguityWarning(clazz, factories);
        if (warning != null) {
            log.warn(warning);
        }
        return factories.get(0);
    }

    /**
     * Load all the services of a given type, sorted by {@link PrioritizedFactory#priority() priority},
     * highest first.
     *
     * <p>Use this when every service is used. When only one is used, use {@link #loadFactory(Class)}
     * instead, which also warns when the choice depends on classpath order.</p>
     *
     * @param clazz the type of service
     * @param <T>   the type of service
     * @return the list of services, empty if none
     */
    public static <T> Collection<T> loadFactories(Class<T> clazz) {
        return loadFactories(clazz, null);
    }

    /**
     * Load all the services of a given type.
     *
     * <p>Utility mechanism around {@code ServiceLoader.load()}</p>
     *
     * <ul>
     *     <li>If classloader is {@code null}, will try {@code ServiceLoader.load(clazz)}</li>
     *     <li>If classloader is not {@code null}, will try {@code ServiceLoader.load(clazz, classloader)}</li>
     *     </ul>
     *
     * <p>If the above return nothing, will fall back to {@code ServiceLoader.load(clazz, $this class loader$)}</p>
     *
     * <p>Services are sorted by {@link PrioritizedFactory#priority() priority}, highest first.
     * Use this when every service is used. When only one is used, use {@link #loadFactory(Class)}
     * instead, which also warns when the choice depends on classpath order.</p>
     *
     * @param clazz       the type of service
     * @param classLoader the classloader to use, may be null
     * @param <T>         the type of service
     * @return the list of services, empty if none
     */
    public static <T> Collection<T> loadFactories(Class<T> clazz, /* @Nullable */ ClassLoader classLoader) {
        return loadSortedByPriority(clazz, classLoader);
    }

    private static <T> List<T> loadSortedByPriority(Class<T> clazz, ClassLoader classLoader) {
        List<T> result;
        if (classLoader != null) {
            result = loadAll(ServiceLoader.load(clazz, classLoader));
        } else {
            // this is equivalent to:
            // ServiceLoader.load(clazz, TCCL);
            result = loadAll(ServiceLoader.load(clazz));
        }
        if (result.isEmpty()) {
            // By default, ServiceLoader.load uses the TCCL, this may not be enough in environment dealing with
            // classloaders differently such as OSGi. So we should try to use the classloader having loaded this
            // class. In OSGi it would be the bundle exposing vert.x and so have access to all its classes.
            result = loadAll(ServiceLoader.load(clazz, ServiceHelper.class.getClassLoader()));
        }
        return sortByPriority(result);
    }

    /**
     * Highest priority first, stable so that equal priorities keep the {@link ServiceLoader} order
     * they came in with.
     */
    static <T> List<T> sortByPriority(List<T> factories) {
        List<T> sorted = new ArrayList<>(factories);
        sorted.sort(Comparator.comparingInt(ServiceHelper::priorityOf).reversed());
        return sorted;
    }

    private static int priorityOf(Object factory) {
        return factory instanceof PrioritizedFactory prioritized
                ? prioritized.priority()
                : PrioritizedFactory.DEFAULT_PRIORITY;
    }

    static <T> String ambiguityWarning(Class<T> clazz, List<T> sorted) {
        if (sorted.isEmpty()) {
            return null;
        }
        int highest = priorityOf(sorted.get(0));
        List<String> tied = sorted.subList(1, sorted.size()).stream()
                .filter(other -> priorityOf(other) == highest)
                .map(other -> other.getClass().getName())
                .toList();
        if (tied.isEmpty()) {
            return null;
        }
        return String.format(
                "Found %d implementations of %s with the same priority on the classpath; using %s and ignoring %s. "
                        + "Which one is used is decided by classpath order and is not stable - "
                        + "remove the ones you do not want, or implement %s on the one that should win.",
                tied.size() + 1,
                clazz.getName(),
                sorted.get(0).getClass().getName(),
                tied,
                PrioritizedFactory.class.getName());
    }

    /**
     * Load all the services from a ServiceLoader.
     *
     * @param loader the loader
     * @param <T>    the type of service
     * @return the list of services, empty if none
     */
    private static <T> List<T> loadAll(ServiceLoader<T> loader) {
        List<T> list = new ArrayList<>();
        loader.iterator().forEachRemaining(list::add);
        return list;
    }
}
