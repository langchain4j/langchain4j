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
     * Load the first available service of a given type.
     *
     * @param clazz the type of service
     * @param <T>   the type of service
     * @return the first service, null if none
     */
    public static <T> T loadFactory(Class<T> clazz) {
        Collection<T> factories = loadFactories(clazz, null);
        return factories.isEmpty() ? null : factories.iterator().next();
    }

    /**
     * Load all the services of a given type.
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
     * @param clazz       the type of service
     * @param classLoader the classloader to use, may be null
     * @param <T>         the type of service
     * @return the list of services, empty if none
     */
    public static <T> Collection<T> loadFactories(Class<T> clazz, /* @Nullable */ ClassLoader classLoader) {
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
        result = sortByPriority(result);
        warnIfAmbiguous(clazz, result);
        return result;
    }

    /**
     * Every caller of this takes the first service and ignores the rest, so a second implementation
     * on the classpath is decided by whatever order the {@link ServiceLoader} happened to enumerate
     * - which can differ between a development run, a shaded jar and a container image. That is
     * worth saying out loud rather than leaving someone to discover that their JSON, or their
     * prompt templating, is not the implementation they thought.
     */
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

    private static <T> void warnIfAmbiguous(Class<T> clazz, List<T> found) {
        if (found.size() > 1) {
            log.warn(
                    "Found {} implementations of {} on the classpath; using {} and ignoring {}. "
                            + "Which one is used is decided by classpath order and is not stable - "
                            + "remove the ones you do not want.",
                    found.size(),
                    clazz.getName(),
                    found.get(0).getClass().getName(),
                    found.subList(1, found.size()).stream()
                            .map(other -> other.getClass().getName())
                            .toList());
        }
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
