package dev.langchain4j.model.moderation.mock;

import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A {@link ModerationModel} for tests: it returns the same moderation for every request (or the moderation computed
 * by a function) and records the requests.
 */
public class ModerationModelMock implements ModerationModel {

    private final Function<ModerationRequest, Moderation> moderation;
    private final List<ModerationRequest> requests = new CopyOnWriteArrayList<>();

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

    public static ModerationModelMock thatNeverFlags() {
        return new ModerationModelMock(Moderation.notFlagged());
    }

    public static ModerationModelMock thatAlwaysFlags(String flaggedText) {
        return new ModerationModelMock(Moderation.flagged(flaggedText));
    }

    public static ModerationModelMock thatModerates(Function<ModerationRequest, Moderation> moderation) {
        return new ModerationModelMock(moderation);
    }
}
