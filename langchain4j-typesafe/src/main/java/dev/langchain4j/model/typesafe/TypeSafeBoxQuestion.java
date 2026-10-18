package dev.langchain4j.model.typesafe;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.decision.request.Question;
import java.util.Objects;

/**
 * A TypeSafe <a href="https://docs.typesafe.ai/go/box">Box</a> question: where something is in an image of the input,
 * answered with a {@link TypeSafeBoxAnswer}.
 * <pre>{@code
 * DecisionRequest request = DecisionRequest.builder()
 *         .inputFields(Map.of("photo", ImageContent.from(base64Image, "image/jpeg")))
 *         .question("mug_visible", YesNoQuestion.of("Is there a coffee mug in `photo`?"))
 *         .question("mug", TypeSafeBoxQuestion.of("the coffee mug in `photo`"))
 *         .build();
 *
 * TypeSafeBoxAnswer mug = decisionModel.decide(request).answer("mug", TypeSafeBoxAnswer.class);
 * }</pre>
 * The answer is always a box, even when the target is not in the image, so ask a {@code YesNoQuestion} in the same
 * request when the target may be absent.
 * <p>
 * Box questions are an experimental feature of the TypeSafe API, and need Jev 1.14 or later.
 *
 * @since 1.23.0
 */
@Experimental
public final class TypeSafeBoxQuestion implements Question {

    private final String text;

    private TypeSafeBoxQuestion(String text) {
        this.text = ensureNotBlank(text, "text");
    }

    /**
     * What to find, and in which image, for example "the coffee mug in `photo`".
     */
    @Override
    public String text() {
        return text;
    }

    /**
     * Creates a Box question that finds what the given text describes, for example "the coffee mug in `photo`".
     */
    public static TypeSafeBoxQuestion of(String text) {
        return new TypeSafeBoxQuestion(text);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypeSafeBoxQuestion that)) return false;
        return Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }

    @Override
    public String toString() {
        return "TypeSafeBoxQuestion{text='" + text + "'}";
    }
}
