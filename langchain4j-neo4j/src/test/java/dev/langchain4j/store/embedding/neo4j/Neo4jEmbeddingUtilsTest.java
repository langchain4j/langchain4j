package dev.langchain4j.store.embedding.neo4j;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Neo4jEmbeddingUtilsTest {

    @Test
    void test_sanitize() {
        assertEquals("````", sanitize("``"));
        assertEquals("``````", sanitize("\\u0060\\u0060\\u0060"));
        assertEquals("`Hello```", sanitize("Hello`"));
        assertEquals("`Hi````there`", sanitize("Hi````there"));
        assertEquals("`Hi``````there`", sanitize("Hi`````there"));
        assertEquals("```a``b``c```", sanitize("`a`b`c`"));
        assertEquals("```a``b``c``d```", sanitize("\u0060a`b`c\u0060d\u0060"));
        assertEquals("```a``b``c``d```", sanitize("\\u0060a`b`c\\u0060d\\u0060"));
        assertEquals("`Foo ```", sanitize("Foo \\u0060"));
        assertEquals("ABC", sanitize("ABC"));
        assertEquals("`A C`", sanitize("A C"));
        assertEquals("`A`` C`", sanitize("A` C"));
        assertEquals("ALabel", sanitize("ALabel"));
        assertEquals("`A Label`", sanitize("A Label"));
        assertEquals("`A ``Label`", sanitize("A `Label"));
        assertEquals("```A ``Label`", sanitize("`A `Label"));
        assertEquals("```A ``Label`", sanitize("`A `Label"));
        assertEquals("`Emoticon ⚡️sanitize`", sanitize("Emoticon ⚡️sanitize"));
        assertEquals("`Foo ```", sanitize("Foo \u0060"));
        assertEquals("`Foo``bar`", sanitize("Foo\\`bar"));
        assertEquals("`Foo\\``bar`", sanitize("Foo\\\\`bar"));
        assertEquals("ᑖ", sanitize("ᑖ"));
        assertEquals("`⚡️`", sanitize("⚡️"));
        assertEquals("uᑖ", sanitize("\\u0075\\u1456"));
        assertEquals("ᑖ", sanitize("\u1456"));
        assertEquals("`something\\u005C\\u00751456`", sanitize("something\\u005C\\u00751456"));
        assertEquals("`\\u005C\\u00750060`", sanitize("\\u005Cu0060"));
        assertEquals("`\\```", sanitize("\\u005C\\u0060"));
        assertEquals("`x\\y`", sanitize("x\\y"));
        assertEquals("`x\\y`", sanitize("x\\\\y"));
        assertEquals("`x\\\\y`", sanitize("x\\\\\\\\y"));
        assertEquals("`x``y`", sanitize("x\\`y"));
        assertEquals("`Foo ```", sanitize("Foo \\u0060"));
    }

    private String sanitize(String value) {
        return Neo4jEmbeddingUtils.sanitizeOrThrows(value, "ignored");
    }
}
