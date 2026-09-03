package dev.langchain4j.reactive.streaming;

import static dev.langchain4j.reactive.streaming.ReactiveStreamingTestSupport.TCK_PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS;
import static dev.langchain4j.reactive.streaming.ReactiveStreamingTestSupport.tckTestEnvironment;
import static org.reactivestreams.FlowAdapters.toPublisher;

import dev.langchain4j.internal.DemandDecouplingPublisher;
import java.util.concurrent.Flow;
import mutiny.zero.BackpressureStrategy;
import mutiny.zero.TubeConfiguration;
import mutiny.zero.ZeroPublisher;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;

/**
 * Reactive Streams TCK for {@link DemandDecouplingPublisher} over the same kind of source it wraps in production:
 * a mutiny-zero buffering tube fed from another thread.
 * <p>
 * The bare tube does not pass this verification - {@code required_spec102} and {@code required_spec107} fail
 * intermittently, which is what the operator exists to work around (see the operator's javadoc). This test is
 * therefore also the check to run when removing it: point it at the raw tube, and if it stays green over repeated
 * runs the upstream defect is fixed.
 */
public class DemandDecouplingPublisherTckTest extends PublisherVerification<Integer> {

    private static final int BUFFER_SIZE = 256;

    public DemandDecouplingPublisherTckTest() {
        super(tckTestEnvironment(), TCK_PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS);
    }

    @Override
    public long maxElementsFromPublisher() {
        return BUFFER_SIZE;
    }

    @Override
    public Publisher<Integer> createPublisher(long elements) {
        return toPublisher(new DemandDecouplingPublisher<>(tube(elements, null), BUFFER_SIZE));
    }

    @Override
    public Publisher<Integer> createFailedPublisher() {
        return toPublisher(new DemandDecouplingPublisher<>(tube(0, new RuntimeException("boom")), BUFFER_SIZE));
    }

    /** A tube fed from a separate thread - the arrangement in which the upstream demand race shows up. */
    private static Flow.Publisher<Integer> tube(long elements, Throwable failure) {
        TubeConfiguration config = new TubeConfiguration()
                .withBackpressureStrategy(BackpressureStrategy.BUFFER)
                .withBufferSize(BUFFER_SIZE);
        return ZeroPublisher.create(config, t -> {
            Thread producer = new Thread(() -> {
                if (failure != null) {
                    t.fail(failure);
                    return;
                }
                for (long i = 0; i < elements && !t.cancelled(); i++) {
                    t.send((int) i);
                }
                if (!t.cancelled()) {
                    t.complete();
                }
            });
            producer.setDaemon(true);
            t.whenTerminates(producer::interrupt);
            producer.start();
        });
    }
}
