package dev.langchain4j.model.moderation.mock;

import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import dev.langchain4j.test.mock.MockModerationModel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A {@link ModerationModel} for tests: it returns the same moderation for every request (or the moderation computed
 * by a function) and records the requests.
 *
 * @deprecated use {@link MockModerationModel} (package {@code dev.langchain4j.test.mock}) from the
 * {@code langchain4j-test} module. Unlike this class, it answers one request per scripted step and fails when the
 * script runs out; end the script with {@link MockModerationModel#thenRepeats()} to answer every further request with
 * its last step. {@code thatNeverFlags()} is now {@link MockModerationModel#thatDoesNotFlag()} and
 * {@code thatAlwaysFlags(...)} is now {@link MockModerationModel#thatFlags(String)}. {@code requests()} has the same
 * name and returns a copy, and {@code thatModerates(...)} and {@code withListeners(...)} have the same names.
 * This class will be removed in 1.26.0 at the earliest.
 */
@Deprecated(since = "1.24.0", forRemoval = true)
public class ModerationModelMock implements ModerationModel {

    private final Function<ModerationRequest, Moderation> moderation;
    private final List<ModerationRequest> requests = new CopyOnWriteArrayList<>();
    private List<ModerationModelListener> listeners = List.of();

    /**
     * @deprecated use {@link MockModerationModel#thatModerates(Function)} followed by
     * {@link MockModerationModel#thenRepeats()}: {@code MockModerationModel.thatModerates(request -> moderation)
     * .thenRepeats()}. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public ModerationModelMock(Moderation moderation) {
        this(request -> moderation);
    }

    private ModerationModelMock(Function<ModerationRequest, Moderation> moderation) {
        this.moderation = moderation;
    }

    @Override
    public ModerationResponse doModerate(ModerationRequest moderationRequest) {
        requests.add(moderationRequest);
        return ModerationResponse.builder()
                .moderation(moderation.apply(moderationRequest))
                .build();
    }

    public List<ModerationRequest> requests() {
        return requests;
    }

    /**
     * @deprecated use {@link MockModerationModel#thatDoesNotFlag()} followed by
     * {@link MockModerationModel#thenRepeats()}. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public static ModerationModelMock thatNeverFlags() {
        return new ModerationModelMock(Moderation.notFlagged());
    }

    /**
     * @deprecated use {@link MockModerationModel#thatFlags(String)} followed by
     * {@link MockModerationModel#thenRepeats()}. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public static ModerationModelMock thatAlwaysFlags(String flaggedText) {
        return new ModerationModelMock(Moderation.flagged(flaggedText));
    }

    /**
     * @deprecated use {@link MockModerationModel#thatModerates(Function)} followed by
     * {@link MockModerationModel#thenRepeats()}. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public static ModerationModelMock thatModerates(Function<ModerationRequest, Moderation> moderation) {
        return new ModerationModelMock(moderation);
    }

    /**
     * @deprecated use {@link MockModerationModel#withListeners(ModerationModelListener...)}. Removed together with this
     * class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public ModerationModelMock withListeners(ModerationModelListener... listeners) {
        this.listeners = List.of(listeners);
        return this;
    }

    @Override
    public List<ModerationModelListener> listeners() {
        return listeners;
    }
}
