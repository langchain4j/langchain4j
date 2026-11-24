package dev.langchain4j.data.media;

import static dev.langchain4j.data.media.TestHttpServer.ok;
import static dev.langchain4j.data.media.TestHttpServer.redirect;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.spi.ExecutorProvider;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Tests which threads {@link DefaultMediaFetcher} uses. {@link ExecutorProvider#set(ExecutorProvider)} is process-wide,
 * so this class runs alone and sequentially.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class DefaultMediaFetcherExecutorTest {

    private static final byte[] IMAGE = "fake-png-bytes".getBytes(UTF_8);

    private final List<Thread> lookupThreads = new CopyOnWriteArrayList<>();
    private TestHttpServer server;

    @AfterEach
    void cleanUp() {
        ExecutorProvider.set(null);
        if (server != null) {
            server.close();
        }
    }

    /**
     * An executor that runs each task on a new thread named {@code <name>-<n>}, and counts the tasks.
     */
    private static final class RecordingExecutor implements Executor {

        private final String name;
        private final ExecutorService threads;
        private final AtomicInteger tasks = new AtomicInteger();

        private RecordingExecutor(String name) {
            AtomicInteger counter = new AtomicInteger();
            this.name = name;
            this.threads = Executors.newCachedThreadPool(task -> {
                Thread thread = new Thread(task, name + "-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
        }

        @Override
        public void execute(Runnable task) {
            tasks.incrementAndGet();
            threads.execute(task);
        }
    }

    /**
     * A fetcher that resolves every host name to 127.0.0.1, treats it as public, and records the threads that look up
     * the host names.
     */
    private DefaultMediaFetcher fetcher() {
        return new DefaultMediaFetcher(
                DefaultMediaFetcher.builder(),
                host -> {
                    lookupThreads.add(Thread.currentThread());
                    return new InetAddress[] {InetAddress.getByName("127.0.0.1")};
                },
                address -> true);
    }

    @Test
    void fetchAsync_should_run_on_the_default_executor_of_langchain4j() {
        RecordingExecutor executor = new RecordingExecutor("host-executor");
        ExecutorProvider.set(() -> executor);
        server = TestHttpServer.http("127.0.0.1").on("/cat.png", ok(IMAGE)).on("/redirect", redirect("/cat.png"));

        assertThat(fetcher().fetchAsync(MediaFetchRequest.from(server.url("media.test", "/redirect"))))
                .succeedsWithin(10, SECONDS)
                .isEqualTo(FetchedMedia.from(IMAGE));

        // the first request, the redirect and the answer to the second request
        assertThat(executor.tasks).hasValue(3);
        assertThat(lookupThreads)
                .isNotEmpty()
                .allSatisfy(thread -> assertThat(thread.getName()).startsWith("host-executor-"));
    }

    @Test
    void fetch_should_run_on_the_calling_thread() {
        RecordingExecutor executor = new RecordingExecutor("host-executor");
        ExecutorProvider.set(() -> executor);
        server = TestHttpServer.http("127.0.0.1").on("/cat.png", ok(IMAGE)).on("/redirect", redirect("/cat.png"));

        assertThat(fetcher().fetch(MediaFetchRequest.from(server.url("media.test", "/redirect"))).bytes())
                .isEqualTo(IMAGE);

        assertThat(executor.tasks).hasValue(0);
        assertThat(lookupThreads).isNotEmpty().containsOnly(Thread.currentThread());
    }

    @Test
    void fetchAsync_should_use_the_default_executor_that_is_registered_when_it_downloads() {
        server = TestHttpServer.http("127.0.0.1").on("/cat.png", ok(IMAGE));
        DefaultMediaFetcher fetcher = fetcher();
        RecordingExecutor first = new RecordingExecutor("first");
        RecordingExecutor second = new RecordingExecutor("second");

        ExecutorProvider.set(() -> first);
        assertThat(fetcher.fetchAsync(MediaFetchRequest.from(server.url("media.test", "/cat.png"))))
                .succeedsWithin(10, SECONDS);
        assertThat(lookupThreads).isNotEmpty().allSatisfy(thread -> assertThat(thread.getName())
                .startsWith("first-"));
        lookupThreads.clear();

        ExecutorProvider.set(() -> second);
        assertThat(fetcher.fetchAsync(MediaFetchRequest.from(server.url("media.test", "/cat.png"))))
                .succeedsWithin(10, SECONDS);
        assertThat(lookupThreads).isNotEmpty().allSatisfy(thread -> assertThat(thread.getName())
                .startsWith("second-"));

        assertThat(first.tasks).hasValue(2);
        assertThat(second.tasks).hasValue(2);
    }

    @Test
    void fetchAsync_should_fail_when_the_default_executor_rejects_the_download_and_fetch_should_still_work() {
        ExecutorProvider.set(() -> task -> {
            throw new RejectedExecutionException("busy");
        });
        server = TestHttpServer.http("127.0.0.1").on("/cat.png", ok(IMAGE));
        DefaultMediaFetcher fetcher = fetcher();

        assertThat(fetcher.fetchAsync(MediaFetchRequest.from(server.url("media.test", "/cat.png"))))
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isExactlyInstanceOf(RejectedExecutionException.class)
                .withMessage("busy");
        assertThat(server.hits()).isZero();

        assertThat(fetcher.fetch(MediaFetchRequest.from(server.url("media.test", "/cat.png"))).bytes())
                .isEqualTo(IMAGE);
    }

    @Test
    void fetchAsync_should_complete_when_the_default_executor_rejects_a_later_step() {
        RecordingExecutor executor = new RecordingExecutor("host-executor");
        AtomicInteger accepted = new AtomicInteger();
        ExecutorProvider.set(() -> task -> {
            if (accepted.incrementAndGet() > 1) {
                throw new RejectedExecutionException("busy");
            }
            executor.execute(task);
        });
        server = TestHttpServer.http("127.0.0.1").on("/cat.png", ok(IMAGE));

        // the answer is then handled on the thread that received it
        assertThat(fetcher().fetchAsync(MediaFetchRequest.from(server.url("media.test", "/cat.png"))))
                .succeedsWithin(10, SECONDS)
                .isEqualTo(FetchedMedia.from(IMAGE));
        assertThat(executor.tasks).hasValue(1);
    }
}
