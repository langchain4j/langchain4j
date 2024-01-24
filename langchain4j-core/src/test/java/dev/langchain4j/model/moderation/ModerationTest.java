package dev.langchain4j.model.moderation;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;


class ModerationTest implements WithAssertions {
    @Test
    public void test_methods() {
        {
            Moderation moderation = new Moderation();
            assertThat(moderation.flagged()).isFalse();
            assertThat(moderation.flaggedText()).isNull();
            assertThat(moderation)
                    .hasToString("Moderation { flagged = false, flaggedText = null }");
        }
        {
            Moderation moderation = new Moderation("flaggedText");
            assertThat(moderation.flagged()).isTrue();
            assertThat(moderation.flaggedText()).isEqualTo("flaggedText");
            assertThat(moderation)
                    .hasToString("Moderation { flagged = true, flaggedText = \"flaggedText\" }");
        }
    }

    @Test
    public void test_equals_hashCode() {
        Moderation flagged1 = new Moderation("flaggedText");
        Moderation flagged2 = new Moderation("flaggedText");

        Moderation unflagged1 = new Moderation();
        Moderation unflagged2 = new Moderation();

        Moderation flagged3 = new Moderation("other text");
        Moderation flagged4 = new Moderation("other text");

        assertThat(unflagged1)
                .isEqualTo(unflagged1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(unflagged2)
                .hasSameHashCodeAs(unflagged2)
                .isNotEqualTo(flagged1)
                .doesNotHaveSameHashCodeAs(flagged1)
                .isNotEqualTo(flagged2)
                .doesNotHaveSameHashCodeAs(flagged2);

        assertThat(flagged1)
                .isEqualTo(flagged1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(flagged2)
                .hasSameHashCodeAs(flagged2)
                .isNotEqualTo(unflagged1)
                .doesNotHaveSameHashCodeAs(unflagged1)
                .isNotEqualTo(unflagged2)
                .doesNotHaveSameHashCodeAs(unflagged2)
                .isNotEqualTo(flagged3)
                .doesNotHaveSameHashCodeAs(flagged3);

        assertThat(flagged3)
                .isEqualTo(flagged3)
                .isEqualTo(flagged4)
                .hasSameHashCodeAs(flagged4);
    }

    @Test
    public void test_builders() {
        assertThat(new Moderation("flaggedText"))
                .isEqualTo(Moderation.flagged("flaggedText"));

        assertThat(new Moderation())
                .isEqualTo(Moderation.notFlagged());
    }

}