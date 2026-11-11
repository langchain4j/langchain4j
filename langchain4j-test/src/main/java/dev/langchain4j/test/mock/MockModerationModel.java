package dev.langchain4j.test.mock;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import java.util.List;
import java.util.function.Function;

/**
 * A {@link ModerationModel} for tests: it needs no network, returns a fixed or computed moderation, and records the
 * requests it receives.
 */
@Experimental
public class MockModerationModel implements ModerationModel {

    private final Function<ModerationRequest, Moderation> moderation;
    private final RecordedRequests<ModerationRequest> requests = new RecordedRequests<>("moderation");
    private List<ModerationModelListener> listeners = List.of();

    MockModerationModel(Function<ModerationRequest, Moderation> moderation) {
        this.moderation = ensureNotNull(moderation, "moderation");
    }

    @Override
    public ModerationResponse doModerate(ModerationRequest moderationRequest) {
        requests.add(moderationRequest);
        return ModerationResponse.builder()
                .moderation(moderation.apply(moderationRequest))
                .build();
    }

    /**
     * A copy of the requests received so far, in the order they arrived.
     */
    public List<ModerationRequest> requests() {
        return requests.all();
    }

    /**
     * The only request received so far.
     *
     * @throws IllegalStateException if the mock received no request or several requests
     */
    public ModerationRequest request() {
        return requests.only();
    }

    /**
     * A model that does not flag anything.
     */
    public static MockModerationModel thatNeverFlags() {
        return new MockModerationModel(ignored -> Moderation.notFlagged());
    }

    /**
     * A model that flags every request, reporting the given text as the flagged one.
     */
    public static MockModerationModel thatAlwaysFlags(String flaggedText) {
        return new MockModerationModel(ignored -> Moderation.flagged(flaggedText));
    }

    /**
     * A model that computes the moderation from each request.
     */
    public static MockModerationModel thatModerates(Function<ModerationRequest, Moderation> moderation) {
        return new MockModerationModel(moderation);
    }

    /**
     * A model whose requests all fail with exactly the given exception.
     */
    public static MockModerationModel thatFails(RuntimeException exception) {
        ensureNotNull(exception, "exception");
        return new MockModerationModel(ignored -> {
            throw exception;
        });
    }

    /**
     * Notifies the given listeners of each call, like a real model does.
     */
    public MockModerationModel withListeners(ModerationModelListener... listeners) {
        this.listeners = List.of(listeners);
        return this;
    }

    /**
     * Notifies the given listeners of each call, like a real model does.
     */
    public MockModerationModel withListeners(List<ModerationModelListener> listeners) {
        this.listeners = List.copyOf(listeners);
        return this;
    }

    @Override
    public List<ModerationModelListener> listeners() {
        return listeners;
    }
}
