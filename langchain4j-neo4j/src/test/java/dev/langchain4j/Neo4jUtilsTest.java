package dev.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Neo4jUtilsTest {

    @Test
    void test_sanitize() {
        assertThat(sanitize("``")).isEqualTo("````");
        assertThat(sanitize("\\u0060\\u0060\\u0060")).isEqualTo("``````");
        assertThat(sanitize("Hello`")).isEqualTo("`Hello```");
        assertThat(sanitize("Hi````there")).isEqualTo("`Hi````there`");
        assertThat(sanitize("Hi`````there")).isEqualTo("`Hi``````there`");
        assertThat(sanitize("`a`b`c`")).isEqualTo("```a``b``c```");
        assertThat(sanitize("\u0060a`b`c\u0060d\u0060")).isEqualTo("```a``b``c``d```");
        assertThat(sanitize("\\u0060a`b`c\\u0060d\\u0060")).isEqualTo("```a``b``c``d```");
        assertThat(sanitize("Foo \\u0060")).isEqualTo("`Foo ```");
        assertThat(sanitize("ABC")).isEqualTo("ABC");
        assertThat(sanitize("A C")).isEqualTo("`A C`");
        assertThat(sanitize("A` C")).isEqualTo("`A`` C`");
        assertThat(sanitize("ALabel")).isEqualTo("ALabel");
        assertThat(sanitize("A Label")).isEqualTo("`A Label`");
        assertThat(sanitize("A `Label")).isEqualTo("`A ``Label`");
        assertThat(sanitize("`A `Label")).isEqualTo("```A ``Label`");
        assertThat(sanitize("`A `Label")).isEqualTo("```A ``Label`");
        assertThat(sanitize("Emoticon ⚡️sanitize")).isEqualTo("`Emoticon ⚡️sanitize`");
        assertThat(sanitize("Foo \u0060")).isEqualTo("`Foo ```");
        assertThat(sanitize("Foo\\`bar")).isEqualTo("`Foo``bar`");
        assertThat(sanitize("Foo\\\\`bar")).isEqualTo("`Foo\\``bar`");
        assertThat(sanitize("ᑖ")).isEqualTo("ᑖ");
        assertThat(sanitize("⚡️")).isEqualTo("`⚡️`");
        assertThat(sanitize("\\u0075\\u1456")).isEqualTo("uᑖ");
        assertThat(sanitize("\u1456")).isEqualTo("ᑖ");
        assertThat(sanitize("something\\u005C\\u00751456")).isEqualTo("`something\\u005C\\u00751456`");
        assertThat(sanitize("\\u005Cu0060")).isEqualTo("`\\u005C\\u00750060`");
        assertThat(sanitize("\\u005C\\u0060")).isEqualTo("`\\```");
        assertThat(sanitize("x\\y")).isEqualTo("`x\\y`");
        assertThat(sanitize("x\\\\y")).isEqualTo("`x\\y`");
        assertThat(sanitize("x\\\\\\\\y")).isEqualTo("`x\\\\y`");
        assertThat(sanitize("x\\`y")).isEqualTo("`x``y`");
        assertThat(sanitize("Foo \\u0060")).isEqualTo("`Foo ```");
    }

    private String sanitize(String value) {
        return Neo4jUtils.sanitizeOrThrows(value, "ignored");
    }
}
