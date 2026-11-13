package dev.langchain4j.test.mock;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import java.util.List;
import java.util.function.Function;

/**
 * A {@link ModerationModel} for tests: it needs no network, moderates the way the test tells it to, and records the
 * requests it receives.
 * <p>
 * Script the answers step by step, one step per request. One call of {@code moderate(...)} is one request, also when it
 * moderates several messages. For example, a model that flags nothing, however often it is called:
 * <pre>{@code
 * MockModerationModel model = MockModerationModel.thatDoesNotFlag().thenRepeats();
 * }</pre>
 * Every {@code that...} method creates a mock with its first step, and every {@code then...} method adds a step. Each
 * step answers exactly one request. A request after the last step fails with an {@link IllegalStateException} that
 * shows the texts of the request, so a test notices when the code calls the model more often than the script expects,
 * unless the code under test handles the error itself. So a mock that is called more than once, for example to
 * moderate every message of a conversation, ends its script with {@link #thenRepeats()}, which answers every further
 * request with the last step.
 * <p>
 * A script can let a request fail and the next one succeed, for example to test what your application does when the
 * moderation service times out for one message, with LangChain4j's {@link TimeoutException}, and then works again:
 * <pre>{@code
 * MockModerationModel model = MockModerationModel.thatFails(new TimeoutException("Read timed out"))
 *         .thenDoesNotFlag()
 *         .thenRepeats();
 * }</pre>
 * Or it can flag the first message and accept the next ones:
 * {@code MockModerationModel.thatFlags("How do I hack ...").thenDoesNotFlag().thenRepeats()}. To flag a message by its
 * content, compute the moderation from the request with {@link #thatModerates(Function)}.
 * <p>
 * Like on every mock in this package, {@code thatResponds(...)} and {@code thenResponds(...)} give a step as the whole
 * response, here a {@link ModerationResponse}: the request is answered with exactly that response, including its
 * metadata.
 * <p>
 * A mock remembers which steps were taken and which requests it received, so create a new one for each test. A mock
 * shared between tests, for example as a bean, fails with a clear message as soon as its script runs out, unless the
 * script ends with {@link #thenRepeats()}. The steps are taken in the order the requests arrive. A mock keeps every
 * request it receives, so it is not meant for load tests or other long runs.
 */
@Experimental
public class MockModerationModel implements ModerationModel {

    private static final String MOCK_NAME = "MockModerationModel";

    /**
     * One step of the script: the response to one request.
     */
    interface Step {

        ModerationResponse respond(ModerationRequest request);
    }

    private final Script<Step> script = new Script<>(MOCK_NAME);
    private final RecordedRequests<ModerationRequest> requests = new RecordedRequests<>("moderation");
    private List<ModerationModelListener> listeners = List.of();

    MockModerationModel(Step firstStep) {
        script.add(firstStep);
    }

    /**
     * Adds a step: the next request is flagged, with the given text reported as the flagged one. The text is not
     * compared with the request, see {@link #thatFlags(String)}.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockModerationModel thenFlags(String flaggedText) {
        return add(flagging(flaggedText));
    }

    /**
     * Adds a step: the next request is not flagged.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockModerationModel thenDoesNotFlag() {
        return add(notFlagging());
    }

    /**
     * Adds a step: the moderation of the next request is computed from the request.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockModerationModel thenModerates(Function<ModerationRequest, Moderation> moderation) {
        return add(computed(moderation));
    }

    /**
     * Adds a step: the next request is answered with exactly the given response, including its metadata, see
     * {@link #thatResponds(ModerationResponse)}.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockModerationModel thenResponds(ModerationResponse response) {
        return add(responding(response));
    }

    /**
     * Adds a step: the next request fails with exactly the given exception.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockModerationModel thenFails(RuntimeException exception) {
        return add(failing(exception));
    }

    /**
     * Answers every further request with the last step of the script. Only the last step repeats; the script does not
     * start over. A last step that computes its moderation computes it again for each request. Without it, a request
     * after the last step fails. It must be the last call of the script.
     *
     * @throws IllegalStateException if the script already ends with {@code thenRepeats()}
     */
    public synchronized MockModerationModel thenRepeats() {
        script.repeatLastStep();
        return this;
    }

    private synchronized MockModerationModel add(Step step) {
        script.add(step);
        return this;
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
    public ModerationResponse doModerate(ModerationRequest moderationRequest) {
        return take(moderationRequest).respond(moderationRequest);
    }

    /**
     * Records the request and takes the step that answers it, in one go, so that concurrent requests get consistent
     * request numbers. A request after the last step gets a step that fails with an {@link IllegalStateException}.
     *
     * @throws IllegalArgumentException if the request is {@code null}; it is then not recorded
     */
    private synchronized Step take(ModerationRequest request) {
        ensureNotNull(request, "request");
        int requestNumber = requests.add(request);
        Step step = script.next();
        return step != null ? step : failing(new IllegalStateException(runOutMessage(request, requestNumber)));
    }

    private String runOutMessage(ModerationRequest request, int requestNumber) {
        return script.runOutMessage(
                requestNumber, "", "The texts of request " + requestNumber + ":" + Script.quoted(request.texts()));
    }

    @Override
    public List<ModerationModelListener> listeners() {
        return listeners;
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
     * The last request received so far.
     *
     * @throws IllegalStateException if the mock received no request
     */
    public ModerationRequest lastRequest() {
        return requests.last();
    }

    /**
     * A model that flags the first request, reporting the given text as the flagged one. The text is not compared with
     * the request: this step flags the next request, whatever it contains. A real moderation model reports the first
     * flagged text of the request, so pass one of the texts that the code under test sends. To flag only some texts,
     * compute the moderation from the request:
     * <pre>{@code
     * MockModerationModel model = MockModerationModel.thatModerates(request -> request.texts().stream()
     *                 .filter(text -> text.contains("hack"))
     *                 .findFirst()
     *                 .map(Moderation::flagged)
     *                 .orElseGet(Moderation::notFlagged))
     *         .thenRepeats();
     * }</pre>
     * Add the next steps with {@code then...} methods, or flag every request: {@code thatFlags("...").thenRepeats()}.
     */
    public static MockModerationModel thatFlags(String flaggedText) {
        return new MockModerationModel(flagging(flaggedText));
    }

    /**
     * A model that does not flag the first request. Add the next steps with {@code then...} methods, or flag nothing:
     * {@code thatDoesNotFlag().thenRepeats()}.
     */
    public static MockModerationModel thatDoesNotFlag() {
        return new MockModerationModel(notFlagging());
    }

    /**
     * A model that computes the moderation of the first request from the request. Add the next steps with
     * {@code then...} methods, or compute the moderation of every request with {@code thenRepeats()}. If the function
     * throws, the request fails with that exception, unchanged.
     */
    public static MockModerationModel thatModerates(Function<ModerationRequest, Moderation> moderation) {
        return new MockModerationModel(computed(moderation));
    }

    /**
     * A model that answers the first request with exactly the given response, including its metadata, to test code
     * that reads it:
     * <pre>{@code
     * MockModerationModel model = MockModerationModel.thatResponds(ModerationResponse.builder()
     *         .moderation(Moderation.flagged("My phone number is 555-0100"))
     *         .metadata(Map.of("detection_type", "pii", "score", 0.97))
     *         .build());
     * }</pre>
     * The metadata depends on the provider, and many moderation models report none. Add the next steps with
     * {@code then...} methods, or answer every request with this response:
     * {@code thatResponds(response).thenRepeats()}.
     */
    public static MockModerationModel thatResponds(ModerationResponse response) {
        return new MockModerationModel(responding(response));
    }

    /**
     * A model whose first request fails with exactly the given exception. To let every request fail, add
     * {@link #thenRepeats()}. To let later requests succeed, for example to test how your application handles a
     * moderation service that is down for a moment, add steps with {@code then...} methods.
     */
    public static MockModerationModel thatFails(RuntimeException exception) {
        return new MockModerationModel(failing(exception));
    }

    private static Step flagging(String flaggedText) {
        ensureNotNull(flaggedText, "flaggedText");
        return computed(request -> Moderation.flagged(flaggedText));
    }

    private static Step notFlagging() {
        return computed(request -> Moderation.notFlagged());
    }

    private static Step computed(Function<ModerationRequest, Moderation> moderation) {
        ensureNotNull(moderation, "moderation");
        return request -> ModerationResponse.builder()
                .moderation(moderation.apply(request))
                .build();
    }

    private static Step responding(ModerationResponse response) {
        ensureNotNull(response, "response");
        return request -> response;
    }

    private static Step failing(RuntimeException exception) {
        ensureNotNull(exception, "exception");
        return request -> {
            throw exception;
        };
    }
}
