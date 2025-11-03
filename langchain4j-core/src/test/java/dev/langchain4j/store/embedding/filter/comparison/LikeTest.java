package dev.langchain4j.store.embedding.filter.comparison;

import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LikeTest {

    @Test
    void testShouldThrowUnsupportedOperationException() {
        Like like = new Like("name", "%Mars%", Like.Operator.LIKE, false);
        Metadata metadata = Metadata.from("name", "Hello from Mars");

        assertThrows(UnsupportedOperationException.class, () -> like.test(metadata));
    }

    @Test
    void testEqualsAndHashCode() {
        Like like1 = new Like("name", "%Mars%", Like.Operator.LIKE, false);
        Like like2 = new Like("name", "%Mars%", Like.Operator.LIKE, false);

        assertEquals(like1, like2);
        assertEquals(like1.hashCode(), like2.hashCode());
    }
}
