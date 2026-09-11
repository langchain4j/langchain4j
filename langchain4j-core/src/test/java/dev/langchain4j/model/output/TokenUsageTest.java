package dev.langchain4j.model.output;

import static dev.langchain4j.model.output.TokenUsage.sum;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class TokenUsageTest implements WithAssertions {
    @Test
    void constructors() {
        assertThat(new TokenUsage()).isEqualTo(new TokenUsage(null, null, null));

        assertThat(new TokenUsage(1)).isEqualTo(new TokenUsage(1, null, 1));

        assertThat(new TokenUsage(1, 2)).isEqualTo(new TokenUsage(1, 2, 3));

        assertThat(new TokenUsage(1, 2, 3)).isEqualTo(new TokenUsage(1, 2, 3));
    }

    @Test
    void accessors() {
        {
            TokenUsage tu = new TokenUsage(1, 2, 3);
            assertThat(tu.inputTokenCount()).isEqualTo(1);
            assertThat(tu.outputTokenCount()).isEqualTo(2);
            assertThat(tu.totalTokenCount()).isEqualTo(3);
        }
        {
            TokenUsage tu = new TokenUsage(null, null, null);
            assertThat(tu.inputTokenCount()).isNull();
            assertThat(tu.outputTokenCount()).isNull();
            assertThat(tu.totalTokenCount()).isNull();
        }
    }

    @Test
    void equals_hash() {
        TokenUsage tu1 = new TokenUsage(1, 2, 3);
        TokenUsage tu2 = new TokenUsage(1, 2, 3);

        assertThat(tu1)
                .isEqualTo(tu1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(tu2)
                .hasSameHashCodeAs(tu2);

        assertThat(new TokenUsage(null, 2, 3)).isNotEqualTo(tu1).doesNotHaveSameHashCodeAs(tu1);

        assertThat(new TokenUsage(1, null, 3)).isNotEqualTo(tu1).doesNotHaveSameHashCodeAs(tu1);

        assertThat(new TokenUsage(1, 2, null)).isNotEqualTo(tu1).doesNotHaveSameHashCodeAs(tu1);
    }

    @Test
    void to_string() {
        assertThat(new TokenUsage(1, 2, 3))
                .hasToString("TokenUsage { inputTokenCount = 1, outputTokenCount = 2, totalTokenCount = 3 }");
        assertThat(new TokenUsage(null, null, null))
                .hasToString("TokenUsage { inputTokenCount = null, outputTokenCount = null, totalTokenCount = null }");
    }

    @Test
    void test_sum() {
        assertThat(sum(new TokenUsage(1, 2, 3), new TokenUsage(4, 5, 6))).isEqualTo(new TokenUsage(5, 7, 9));

        assertThat(sum(new TokenUsage(1, 2, 3), new TokenUsage(null, null, null)))
                .isEqualTo(new TokenUsage(1, 2, 3));

        assertThat(sum(new TokenUsage(null, null, null), new TokenUsage(4, 5, 6)))
                .isEqualTo(new TokenUsage(4, 5, 6));

        assertThat(sum(new TokenUsage(null, null, null), new TokenUsage(null, null, null)))
                .isEqualTo(new TokenUsage(null, null, null));

        assertThat(sum(new TokenUsage(1, 2, 3), null)).isEqualTo(new TokenUsage(1, 2, 3));

        assertThat(sum(null, new TokenUsage(4, 5, 6))).isEqualTo(new TokenUsage(4, 5, 6));
    }

    @Test
    void should_not_recurse_infinitely_when_a_subclass_does_not_override_add() {

        // given two instances of a subclass that only narrows the type and inherits add()
        TokenUsage first = new NarrowingTokenUsage(1, 2);
        TokenUsage second = new NarrowingTokenUsage(4, 5);

        // when
        TokenUsage sum = first.add(second);

        // then the inherited implementation sums them instead of handing the call back and forth
        assertThat(sum.inputTokenCount()).isEqualTo(5);
        assertThat(sum.outputTokenCount()).isEqualTo(7);
        assertThat(sum.totalTokenCount()).isEqualTo(12);
    }

    @Test
    void should_not_recurse_infinitely_when_a_subclass_delegates_add_to_super() {

        TokenUsage first = new SuperDelegatingTokenUsage(1, 2);
        TokenUsage second = new SuperDelegatingTokenUsage(4, 5);

        assertThat(first.add(second)).isEqualTo(new TokenUsage(5, 7, 12));
        assertThat(sum(first, second)).isEqualTo(new TokenUsage(5, 7, 12));
    }

    @Test
    void should_delegate_to_the_subclass_to_preserve_its_extra_data() {

        TokenUsage base = new TokenUsage(1, 2, 3);
        TokenUsage extended = new ExtendedTokenUsage(4, 5, 6);

        assertThat(base.add(extended)).isEqualTo(new ExtendedTokenUsage(5, 7, 9));
        assertThat(sum(base, extended)).isEqualTo(new ExtendedTokenUsage(5, 7, 9));
        assertThat(sum(extended, base)).isEqualTo(new ExtendedTokenUsage(5, 7, 9));
    }

    @Test
    void should_still_delegate_when_only_the_other_subclass_overrides_add() {

        // a subclass that inherits add() must still let a subclass that overrides it do the work,
        // otherwise the provider-specific data carried by the latter would be silently dropped
        TokenUsage narrowing = new NarrowingTokenUsage(1, 2);
        TokenUsage extended = new ExtendedTokenUsage(4, 5, 6);

        assertThat(narrowing.add(extended)).isEqualTo(new ExtendedTokenUsage(5, 7, 9));
        assertThat(sum(narrowing, extended)).isEqualTo(new ExtendedTokenUsage(5, 7, 9));
    }

    /**
     * Mimics the provider subclasses that only narrow the type and inherit {@link TokenUsage#add(TokenUsage)}.
     */
    static class NarrowingTokenUsage extends TokenUsage {

        NarrowingTokenUsage(Integer inputTokenCount, Integer outputTokenCount) {
            super(inputTokenCount, outputTokenCount);
        }
    }

    /**
     * Mimics a subclass that overrides {@link TokenUsage#add(TokenUsage)} but relies on the base implementation.
     */
    static class SuperDelegatingTokenUsage extends TokenUsage {

        SuperDelegatingTokenUsage(Integer inputTokenCount, Integer outputTokenCount) {
            super(inputTokenCount, outputTokenCount);
        }

        @Override
        public TokenUsage add(TokenUsage that) {
            return super.add(that);
        }
    }

    /**
     * Mimics the provider subclasses that override {@link TokenUsage#add(TokenUsage)} to keep their own data.
     */
    static class ExtendedTokenUsage extends TokenUsage {

        ExtendedTokenUsage(Integer inputTokenCount, Integer outputTokenCount, Integer totalTokenCount) {
            super(inputTokenCount, outputTokenCount, totalTokenCount);
        }

        @Override
        public ExtendedTokenUsage add(TokenUsage that) {
            if (that == null) {
                return this;
            }
            return new ExtendedTokenUsage(
                    sum(this.inputTokenCount(), that.inputTokenCount()),
                    sum(this.outputTokenCount(), that.outputTokenCount()),
                    sum(this.totalTokenCount(), that.totalTokenCount()));
        }
    }
}
