package dev.langchain4j.test.mock;

import java.util.concurrent.Executor;

/**
 * The threads the mocks answer asynchronous calls on.
 */
final class MockThreads {

    private MockThreads() {}

    /**
     * An executor that runs each task on a new daemon thread with the given name, like the I/O thread a provider
     * answers on: it carries no context of the caller, not even inheritable thread locals, and tasks that wait for each
     * other never run out of threads. It keeps the context class loader of the caller, so that frameworks that load
     * application classes through it, such as Quarkus in tests, work on it.
     */
    static Executor newThreadPerTask(String name) {
        return task -> {
            ClassLoader callerClassLoader = Thread.currentThread().getContextClassLoader();
            Thread thread = new Thread(null, task, name, 0, false);
            thread.setContextClassLoader(callerClassLoader);
            thread.setDaemon(true);
            thread.start();
        };
    }
}
