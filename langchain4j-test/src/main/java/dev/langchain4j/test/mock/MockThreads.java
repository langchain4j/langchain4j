package dev.langchain4j.test.mock;

import java.util.concurrent.Executor;

/**
 * The threads the mocks answer asynchronous calls on.
 */
final class MockThreads {

    private MockThreads() {}

    /**
     * An executor that runs each task on a new daemon thread with the given name, like the I/O thread a provider
     * answers on: it carries no context of the caller, and tasks that wait for each other never run out of threads.
     */
    static Executor newThreadPerTask(String name) {
        return task -> {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            thread.start();
        };
    }
}
