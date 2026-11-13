package dev.langchain4j.test.mock;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The steps of a scripted mock, taken in order: one per request. A request after the last step gets no step, unless the
 * script ends with {@link #repeatLastStep()}: then the last step answers every further request (the script does not
 * start over). It is not thread-safe: the mock holds its own lock while it uses the script.
 *
 * @param <S> the type of a step
 */
final class Script<S> {

    private final String mockName;
    private final List<S> steps = new ArrayList<>();
    private int nextIndex;
    private boolean repeatsLastStep;

    Script(String mockName) {
        this.mockName = mockName;
    }

    /**
     * Adds a step at the end of the script.
     *
     * @throws IllegalStateException if the script already ends with {@code thenRepeats()}
     */
    void add(S step) {
        ensureNotRepeating();
        steps.add(step);
    }

    /**
     * Lets the last step answer every further request.
     *
     * @throws IllegalStateException if the script already ends with {@code thenRepeats()}
     */
    void repeatLastStep() {
        ensureNotRepeating();
        repeatsLastStep = true;
    }

    void ensureNotRepeating() {
        if (repeatsLastStep) {
            throw new IllegalStateException("thenRepeats() must be the last call of the script: the last"
                    + " step already answers every further request");
        }
    }

    S lastStep() {
        return steps.get(steps.size() - 1);
    }

    /**
     * Whether the last step already answered a request.
     */
    boolean lastStepTaken() {
        return nextIndex == steps.size();
    }

    void replaceLastStep(S step) {
        steps.set(steps.size() - 1, step);
    }

    /**
     * The step that answers the next request, or {@code null} if the script ran out.
     */
    @Nullable
    S next() {
        if (nextIndex < steps.size()) {
            return steps.get(nextIndex++);
        }
        return repeatsLastStep ? lastStep() : null;
    }

    /**
     * The message of the failure of a request after the last step.
     *
     * @param requestNumber the number of the request, starting at 1
     * @param lastStepHint  what to script after the last step, ending with a space, or {@code ""}
     * @param request       what the request asked, to tell it apart from the other requests
     */
    String runOutMessage(int requestNumber, String lastStepHint, String request) {
        return mockName + " received request " + requestNumber + ", but its script has only " + steps.size()
                + (steps.size() == 1 ? " step. " : " steps. ") + lastStepHint
                + "Script one more step, or end the script with thenRepeats() to answer every further request with its"
                + " last step. If the mock is shared between tests, for example as a bean, create a new one for each"
                + " test. " + request;
    }
}
