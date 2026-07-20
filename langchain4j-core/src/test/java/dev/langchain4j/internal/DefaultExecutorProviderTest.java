package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.spi.ExecutorProvider;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DefaultExecutorProviderTest {

    @AfterEach
    void clearProvider() {
        ExecutorProvider.set(null);
    }

    @Test
    void returns_builtin_default_when_no_provider_is_set() {
        // No ServiceLoader provider is registered in core's test classpath, so this is the built-in default.
        assertThat(ExecutorProvider.get()).isNull();
        assertThat(DefaultExecutorProvider.getDefaultExecutor()).isNotNull();
        assertThat(DefaultExecutorProvider.getDefaultExecutorService()).isNotNull();
    }

    @Test
    void set_and_get_round_trip() {
        ExecutorProvider provider = () -> Runnable::run;
        ExecutorProvider.set(provider);
        assertThat(ExecutorProvider.get()).isSameAs(provider);
    }

    @Test
    void executorService_provider_is_honored_by_both_accessors() {
        ExecutorService custom = Executors.newSingleThreadExecutor();
        try {
            ExecutorProvider.set(() -> custom);
            assertThat(DefaultExecutorProvider.getDefaultExecutor()).isSameAs(custom);
            // custom is an ExecutorService, so the ExecutorService accessor uses it too.
            assertThat(DefaultExecutorProvider.getDefaultExecutorService()).isSameAs(custom);
        } finally {
            custom.shutdownNow();
        }
    }

    @Test
    void bare_executor_provider_governs_getDefaultExecutor_but_not_getDefaultExecutorService() {
        Executor bareExecutor = Runnable::run; // an Executor that is NOT an ExecutorService
        ExecutorProvider.set(() -> bareExecutor);

        assertThat(DefaultExecutorProvider.getDefaultExecutor()).isSameAs(bareExecutor);
        // A bare Executor can't satisfy the ExecutorService-typed sites, so they fall back to the built-in.
        assertThat(DefaultExecutorProvider.getDefaultExecutorService())
                .isNotSameAs(bareExecutor)
                .isNotNull();
    }

    @Test
    void falls_back_to_builtin_when_provider_is_cleared() {
        Executor bareExecutor = Runnable::run;
        ExecutorProvider.set(() -> bareExecutor);
        ExecutorProvider.set(null);
        assertThat(ExecutorProvider.get()).isNull();
        assertThat(DefaultExecutorProvider.getDefaultExecutor()).isNotSameAs(bareExecutor);
    }

    @Test
    void provider_returning_null_falls_back_to_builtin() {
        ExecutorProvider.set(() -> null);
        assertThat(DefaultExecutorProvider.getDefaultExecutor()).isNotNull();
        assertThat(DefaultExecutorProvider.getDefaultExecutorService()).isNotNull();
    }
}
