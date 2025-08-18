package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Internal
public class DefaultExecutorProvider {

    private DefaultExecutorProvider() { }

    public static ExecutorService getDefaultExecutorService() {
        return Holder.EXECUTOR_SERVICE;
    }


    private static class Holder {
        private static final boolean VIRTUAL_THREADS_SUPPORTED = detectVirtualThreadSupport();
        private static final ExecutorService EXECUTOR_SERVICE = createExecutorService(VIRTUAL_THREADS_SUPPORTED);

        /**
         * Creates an ExecutorService that uses virtual threads when supported, otherwise platform threads.
         * @return An ExecutorService
         */
        private static ExecutorService createExecutorService(boolean useVirtualThreads) {
            if (useVirtualThreads) {
                return createVirtualThreadExecutorService();
            } else {
                return createPlatformThreadExecutorService();
            }
        }

        private static boolean detectVirtualThreadSupport() {
            try {
                Class.forName("java.lang.Thread").getMethod("startVirtualThread", Runnable.class);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private static ExecutorService createVirtualThreadExecutorService() {
            try {
                // Use reflection to access Executors.newVirtualThreadPerTaskExecutor()
                Method newVirtualThreadPerTaskExecutorMethod =
                        Class.forName("java.util.concurrent.Executors").getMethod("newVirtualThreadPerTaskExecutor");

                return (ExecutorService) newVirtualThreadPerTaskExecutorMethod.invoke(null);
            } catch (Exception e) {
                // Fallback in case of unexpected error
                return createPlatformThreadExecutorService();
            }
        }

        private static ExecutorService createPlatformThreadExecutorService() {
            return new ThreadPoolExecutor(
                    0, Integer.MAX_VALUE,
                    1, TimeUnit.SECONDS,
                    new SynchronousQueue<>()
            );
        }
    }
}
