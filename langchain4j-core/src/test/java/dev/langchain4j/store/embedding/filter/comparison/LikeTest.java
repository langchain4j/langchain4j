package dev.langchain4j.store.embedding.filter.comparison;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;

class LikeTest {

    @Test
    void testShouldThrowUnsupportedOperationException() {
        Like like = new Like("planet", "%Mars%", Like.Operator.LIKE, false);
        Metadata metadata = Metadata.from("planet", "Hello from Mars");

        assertThrows(UnsupportedOperationException.class, () -> like.test(metadata));
    }

    @Test
    void testEqualsAndHashCode() {
        Like like1 = new Like("planet", "%Mars%", Like.Operator.LIKE, false);
        Like like2 = new Like("planet", "%Mars%", Like.Operator.LIKE, false);

        assertEquals(like1, like2);
        assertEquals(like1.hashCode(), like2.hashCode());
    }

    @Test
    void testToString() {
        Like like = new Like("planet", "%Mars%", Like.Operator.LIKE, false);

        String expected = "Like(column=planet, pattern=%Mars%, like-operator=LIKE, negated=false)";
        assertEquals(expected, like.toString());
    }
}
