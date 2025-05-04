package dev.langchain4j.internal;

import static dev.langchain4j.internal.Exceptions.runtime;

import dev.langchain4j.Internal;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
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

    @Nullable
    private static Method isVirtualMethod;

    static {
        try {
            newVirtualThreadPerTaskExecutorMethod = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
            isVirtualMethod = Thread.class.getMethod("isVirtual");
        } catch (Exception e) {
            // Virtual threads not available (pre-Java 21) or other reflection error
            newVirtualThreadPerTaskExecutorMethod = null;
            isVirtualMethod = null;
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
        if (fallback == null) {
            return null;
        } else {
            return fallback.get();
        }
    }

    /**
     * Checks if the current thread is a virtual thread.
     *
     * @return true if the current thread is a virtual thread, false otherwise
     */
    public static boolean isVirtualThread() {
        try {
            if (isVirtualMethod != null) {
                return (boolean) isVirtualMethod.invoke(Thread.currentThread());
            }
        } catch (Exception e) {
            // If the method doesn't exist or fails, assume it's not a virtual thread
            return false;
        }
        return false;
    }
}
