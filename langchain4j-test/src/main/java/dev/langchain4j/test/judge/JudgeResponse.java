package dev.langchain4j.test.judge;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The response of a {@link Judge} to a {@link JudgeRequest}: one {@link JudgeVerdict} per criterion, in the order of
 * the criteria, and the tokens the judge model used. Returned by {@link Judge#evaluate(JudgeRequest)}.
 * Implementations of {@link Judge} create it with {@link #from(List)} or {@link #builder()}.
 */
@Experimental
public final class JudgeResponse {

    private final List<JudgeVerdict> verdicts;
    private final TokenUsage tokenUsage;

    private JudgeResponse(Builder builder) {
        ensureNotNull(builder.verdicts, "verdicts");
        for (int i = 0; i < builder.verdicts.size(); i++) {
            ensureNotNull(builder.verdicts.get(i), "verdict " + (i + 1));
        }
        this.verdicts = List.copyOf(builder.verdicts);
        this.tokenUsage = builder.tokenUsage;
    }

    /**
     * Creates a response with the given verdicts and without token usage, for example in a {@link Judge}
     * implementation.
     *
     * @param verdicts one verdict per criterion, in the order of the criteria
     */
    public static JudgeResponse from(List<JudgeVerdict> verdicts) {
        return builder().verdicts(verdicts).build();
    }

    /**
     * Returns a builder for a response, for example in a {@link Judge} implementation.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * One verdict per criterion of the request, in the order of the criteria.
     */
    public List<JudgeVerdict> verdicts() {
        return verdicts;
    }

    /**
     * The tokens the judge model used for this evaluation, added up over all requests the judge sent. A count that a
     * response did not report is left out of the sum, as {@link TokenUsage#add(TokenUsage)} does. {@code null} if the
     * judge model did not report token usage for any request.
     */
    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JudgeResponse that
                && verdicts.equals(that.verdicts)
                && Objects.equals(tokenUsage, that.tokenUsage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(verdicts, tokenUsage);
    }

    @Override
    public String toString() {
        return "JudgeResponse{verdicts=" + verdicts + ", tokenUsage=" + tokenUsage + '}';
    }

    /**
     * Builder for {@link JudgeResponse}.
     */
    public static final class Builder {

        private List<JudgeVerdict> verdicts;
        private TokenUsage tokenUsage;

        private Builder() {}

        /**
         * Sets the verdicts: one per criterion, in the order of the criteria. Required.
         */
        public Builder verdicts(List<JudgeVerdict> verdicts) {
            this.verdicts = verdicts == null ? null : new ArrayList<>(verdicts);
            return this;
        }

        /**
         * Sets the tokens used by the judge model. Optional.
         */
        public Builder tokenUsage(TokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return this;
        }

        /**
         * Builds the response.
         *
         * @throws IllegalArgumentException if the verdicts are not set or contain {@code null}
         */
        public JudgeResponse build() {
            return new JudgeResponse(this);
        }
    }
}
