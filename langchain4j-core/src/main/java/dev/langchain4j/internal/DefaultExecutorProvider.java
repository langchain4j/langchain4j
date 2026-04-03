package dev.langchain4j.internal;

import static dev.langchain4j.internal.VirtualThreadUtils.createVirtualThreadExecutor;

import dev.langchain4j.Internal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Internal
public class DefaultExecutorProvider {

    private DefaultExecutorProvider() {}

    public static ExecutorService getDefaultExecutorService() {
        return Holder.EXECUTOR_SERVICE;
    }

    private static class Holder {
        private static final ExecutorService EXECUTOR_SERVICE =
                createVirtualThreadExecutor(Holder::createPlatformThreadExecutorService);

        private static ExecutorService createPlatformThreadExecutorService() {
            return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 1, TimeUnit.SECONDS, new SynchronousQueue<>());
        }
    }
}
