package dev.langchain4j.spi;

import dev.langchain4j.Internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Utility wrapper around {@code ServiceLoader.load()}.
 */
@Internal
public class ServiceHelper {

    /**
     * Utility class, no public constructor.
     */
    private ServiceHelper() {
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
        return result;
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
