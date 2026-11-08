package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class CancellationChainTest {

    @Test
    void should_reject_null_root() {
        assertThatThrownBy(() -> new CancellationChain(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void track_should_return_the_same_future() {
        CompletableFuture<?> root = new CompletableFuture<>();
        CancellationChain chain = new CancellationChain(root);

        CompletableFuture<String> future = new CompletableFuture<>();
        assertThat(chain.track(future)).isSameAs(future);
    }

    @Test
    void cancelling_root_should_cancel_tracked_future() {
        CompletableFuture<?> root = new CompletableFuture<>();
        CancellationChain chain = new CancellationChain(root);

        CompletableFuture<String> tracked = chain.track(new CompletableFuture<>());
        assertThat(tracked.isCancelled()).isFalse();

        root.cancel(true);

        assertThat(tracked.isCancelled()).isTrue();
    }

    @Test
    void tracking_a_future_when_root_is_already_cancelled_should_cancel_it_immediately() {
        CompletableFuture<?> root = new CompletableFuture<>();
        CancellationChain chain = new CancellationChain(root);
        root.cancel(true);

        CompletableFuture<String> tracked = chain.track(new CompletableFuture<>());

        assertThat(tracked.isCancelled()).isTrue();
    }

    @Test
    void normal_completion_of_root_should_not_cancel_tracked_future() {
        CompletableFuture<Object> root = new CompletableFuture<>();
        CancellationChain chain = new CancellationChain(root);

        CompletableFuture<String> tracked = chain.track(new CompletableFuture<>());
        root.complete("done");

        assertThat(tracked.isCancelled()).isFalse();
        assertThat(tracked.isDone()).isFalse();
    }

    @Test
    void cancelled_should_reflect_root_state() {
        CompletableFuture<?> root = new CompletableFuture<>();
        CancellationChain chain = new CancellationChain(root);

        assertThat(chain.cancelled()).isFalse();
        root.cancel(true);
        assertThat(chain.cancelled()).isTrue();
    }
}
