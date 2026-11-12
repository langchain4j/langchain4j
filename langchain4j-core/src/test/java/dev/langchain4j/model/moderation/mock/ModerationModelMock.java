package dev.langchain4j.model.moderation.mock;

import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;
import dev.langchain4j.test.mock.MockModerationModel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A {@link ModerationModel} for tests: it returns the same moderation for every request and records the requests.
 *
 * @deprecated use {@link MockModerationModel} (package {@code dev.langchain4j.test.mock}) from the
 * {@code langchain4j-test} module. {@code requests()} has the same name and returns a copy.
 * This class will be removed in 1.26.0 at the earliest.
 */
@Deprecated(since = "1.24.0", forRemoval = true)
public class ModerationModelMock implements ModerationModel {

    private final Moderation moderation;
    private final List<ModerationRequest> requests = new CopyOnWriteArrayList<>();

    /**
     * @deprecated use {@link MockModerationModel#thatModerates(Function)}:
     * {@code MockModerationModel.thatModerates(request -> moderation)}
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public ModerationModelMock(Moderation moderation) {
        this.moderation = moderation;
    }

    @Override
    public ModerationResponse doModerate(ModerationRequest moderationRequest) {
        requests.add(moderationRequest);
        return ModerationResponse.builder().moderation(moderation).build();
    }

    public List<ModerationRequest> requests() {
        return requests;
    }

    /**
     * @deprecated use {@link MockModerationModel#thatNeverFlags()}
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public static ModerationModelMock thatNeverFlags() {
        return new ModerationModelMock(Moderation.notFlagged());
    }

    /**
     * @deprecated use {@link MockModerationModel#thatAlwaysFlags(String)}
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public static ModerationModelMock thatAlwaysFlags(String flaggedText) {
        return new ModerationModelMock(Moderation.flagged(flaggedText));
    }
}
