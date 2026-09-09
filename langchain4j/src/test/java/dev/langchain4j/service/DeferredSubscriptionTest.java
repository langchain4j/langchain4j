package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DeferredSubscriptionTest {

    /** A {@link Flow.Subscription} that records the demand requested and whether it was cancelled. */
    private static final class RecordingSubscription implements Flow.Subscription {
        final AtomicLong requested = new AtomicLong();
        final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void request(long n) {
            requested.addAndGet(n);
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }
    }

    @Test
    void demand_requested_before_the_real_subscription_is_replayed_when_it_arrives() {
        DeferredSubscription deferred = new DeferredSubscription();
        deferred.request(3);
        deferred.request(4);

        RecordingSubscription real = new RecordingSubscription();
        deferred.setSubscription(real);

        assertThat(real.requested).hasValue(7);
        assertThat(real.cancelled).isFalse();
    }

    @Test
    void demand_requested_after_the_real_subscription_is_forwarded_directly() {
        DeferredSubscription deferred = new DeferredSubscription();
        RecordingSubscription real = new RecordingSubscription();
        deferred.setSubscription(real);

        deferred.request(5);

        assertThat(real.requested).hasValue(5);
    }

    @Test
    void cancel_before_the_real_subscription_runs_the_cancel_action() {
        DeferredSubscription deferred = new DeferredSubscription();
        AtomicBoolean prologueCancelled = new AtomicBoolean();
        deferred.setCancelAction(() -> prologueCancelled.set(true));

        deferred.cancel();

        assertThat(deferred.isCancelled()).isTrue();
        assertThat(prologueCancelled).isTrue();
    }

    @Test
    void cancel_after_the_real_subscription_cancels_the_delegate() {
        DeferredSubscription deferred = new DeferredSubscription();
        RecordingSubscription real = new RecordingSubscription();
        deferred.setSubscription(real);

        deferred.cancel();

        assertThat(real.cancelled).isTrue();
    }

    @Test
    void a_real_subscription_arriving_after_cancel_is_cancelled_immediately() {
        DeferredSubscription deferred = new DeferredSubscription();
        deferred.cancel();

        RecordingSubscription real = new RecordingSubscription();
        deferred.setSubscription(real);

        assertThat(real.cancelled).isTrue();
        assertThat(real.requested).hasValue(0);
    }

    @Test
    void setting_a_cancel_action_after_cancel_runs_it_immediately() {
        DeferredSubscription deferred = new DeferredSubscription();
        deferred.cancel();

        AtomicBoolean ran = new AtomicBoolean();
        deferred.setCancelAction(() -> ran.set(true));

        assertThat(ran).isTrue();
    }

    @Test
    void cancel_is_idempotent() {
        DeferredSubscription deferred = new DeferredSubscription();
        AtomicLong cancelActionRuns = new AtomicLong();
        deferred.setCancelAction(cancelActionRuns::incrementAndGet);

        deferred.cancel();
        deferred.cancel();

        assertThat(cancelActionRuns).hasValue(1);
    }

    @Test
    void request_and_further_signals_are_ignored_after_cancel() {
        DeferredSubscription deferred = new DeferredSubscription();
        deferred.cancel();

        RecordingSubscription real = new RecordingSubscription();
        deferred.setSubscription(real); // cancelled immediately
        deferred.request(9); // no-op

        assertThat(real.requested).hasValue(0);
    }
}
