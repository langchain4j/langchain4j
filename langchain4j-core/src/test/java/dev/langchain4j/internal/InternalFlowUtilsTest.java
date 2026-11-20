package dev.langchain4j.internal;

import static dev.langchain4j.internal.InternalFlowUtils.failPublisherOnThrow;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.junit.jupiter.api.Test;

class InternalFlowUtilsTest {

    @Test
    void failPublisherOnThrow_should_return_the_publisher_of_the_operation() {
        try (SubmissionPublisher<String> publisher = new SubmissionPublisher<>()) {

            assertThat(failPublisherOnThrow(() -> publisher)).isSameAs(publisher);
            assertThat(failPublisherOnThrow(this, "operation()", () -> publisher)).isSameAs(publisher);
        }
    }

    @Test
    void failPublisherOnThrow_should_return_a_failed_publisher_when_the_operation_throws_an_exception() {
        RuntimeException exception = new IllegalArgumentException("invalid request");

        Flow.Publisher<String> publisher = failPublisherOnThrow(() -> {
            throw exception;
        });

        assertThat(signals(publisher)).containsExactly("onSubscribe", exception);
    }

    @Test
    void failPublisherOnThrow_should_return_a_failed_publisher_when_the_operation_throws_an_error() {
        NoClassDefFoundError error = new NoClassDefFoundError("MissingOptionalDependency");

        Flow.Publisher<String> publisher = failPublisherOnThrow(this, "operation()", () -> {
            throw error;
        });

        assertThat(signals(publisher)).containsExactly("onSubscribe", error);
    }

    @Test
    void failPublisherOnThrow_should_name_the_method_that_returned_null() {
        Flow.Publisher<String> publisher = failPublisherOnThrow(this, "operation()", () -> null);

        List<Object> signals = signals(publisher);

        assertThat(signals).hasSize(2);
        assertThat(signals.get(0)).isEqualTo("onSubscribe");
        assertThat((Throwable) signals.get(1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(InternalFlowUtilsTest.class.getName() + ".operation() returned null");
    }

    @Test
    void failPublisherOnThrow_should_fail_when_an_operation_without_a_name_returns_null() {
        Flow.Publisher<String> publisher = failPublisherOnThrow(() -> null);

        List<Object> signals = signals(publisher);

        assertThat(signals).hasSize(2);
        assertThat((Throwable) signals.get(1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The reactive operation returned null");
    }

    @Test
    void failPublisherOnThrow_should_restore_the_interrupt_flag_when_the_operation_throws_an_interrupted_exception() {
        InterruptedException interrupted = new InterruptedException("interrupted");

        Flow.Publisher<String> publisher = failPublisherOnThrow(() -> sneakyThrow(interrupted));
        boolean interruptFlagRestored = Thread.interrupted(); // also clears the flag for the next tests

        assertThat(interruptFlagRestored).isTrue();
        assertThat(signals(publisher)).containsExactly("onSubscribe", interrupted);
    }

    private static <T> List<Object> signals(Flow.Publisher<T> publisher) {
        List<Object> signals = new ArrayList<>();
        publisher.subscribe(new Flow.Subscriber<T>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                signals.add("onSubscribe");
            }

            @Override
            public void onNext(T item) {
                signals.add(item);
            }

            @Override
            public void onError(Throwable error) {
                signals.add(error);
            }

            @Override
            public void onComplete() {
                signals.add("onComplete");
            }
        });
        return signals;
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T sneakyThrow(Throwable throwable) throws E {
        throw (E) throwable;
    }
}
