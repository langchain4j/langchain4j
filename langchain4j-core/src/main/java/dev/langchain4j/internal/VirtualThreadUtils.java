package dev.langchain4j.internal;

import static dev.langchain4j.internal.Exceptions.runtime;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import dev.langchain4j.Internal;
import org.jspecify.annotations.Nullable;

/**
 * Utility class for working with virtual threads introduced in Java 21.
 * <p>
 * This class provides a mechanism to create a virtual thread per task executor
 * through reflection, enabling compatibility with both Java versions that
 * support virtual threads and those that do not.
 *
 * @author Konstantin Pavlov
 */
@Internal
public class VirtualThreadUtils {

    @Nullable
    private static Method newVirtualThreadPerTaskExecutorMethod;

    static {
        try {
            newVirtualThreadPerTaskExecutorMethod = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
        } catch (Exception e) {
            // Virtual threads not available (pre-Java 21) or other reflection error
        }
    }

    /**
     * Creates a virtual thread per task executor using reflection.
     * This allows code to run on both Java 21+ (where virtual threads are available)
     * and earlier Java versions.
     *
     * @param fallback a {@link Supplier} that provides a fallback {@link ExecutorService} when virtual threads are not available.
     * @return an {@link ExecutorService} using virtual threads if supported, otherwise a fallback implementation.
     */
    @Nullable
    public static ExecutorService createVirtualThreadExecutor(Supplier<@Nullable ExecutorService> fallback) {
        try {
            if (newVirtualThreadPerTaskExecutorMethod != null) {
                return (ExecutorService) newVirtualThreadPerTaskExecutorMethod.invoke(null);
            }
        } catch (Exception e) {
            throw runtime("Failed to create virtual thread executor", e);
        }
        return fallback.get();
    }
}
