package dev.langchain4j.jackson3;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The chat-message hierarchy is described to Jackson twice - once here and once by the Jackson 2
 * codec in langchain4j-core - because a mixin is a type belonging to one Jackson version and cannot
 * be shared. So a new {@code ChatMessage} subtype or {@code Content} type has to be registered in
 * both places, and forgetting this one is invisible until somebody opts in to Jackson 3.
 *
 * <p>Compared as source text rather than as registered mixins, because no module has both codecs on
 * its classpath: this one excludes Jackson 2 precisely so that its other tests mean something.
 */
class ChatMessageMixinParityTest {

    private static final Pattern MIXIN_TARGET = Pattern.compile("addMixIn\\(\\s*([A-Za-z0-9_.]+)\\.class");

    private static final Path JACKSON3_CODEC =
            Path.of("src/main/java/dev/langchain4j/jackson3/Jackson3ChatMessageJsonCodec.java");

    private static final Path JACKSON2_CODEC = Path.of(
            "../langchain4j-core/src/main/java/dev/langchain4j/data/message/JacksonChatMessageJsonCodec.java");

    private static Set<String> mixinTargetsOf(Path source) throws IOException {
        // This test reads the two codecs' source, so it only means anything when run from this
        // module. Other modules pull this suite in through the jackson3 profile to get
        // Jackson3ActiveTest, and there the sources are not there to read.
        assumeTrue(Files.exists(source), "not run from the module directory, so the source is not readable");

        Set<String> targets = new LinkedHashSet<>();
        Matcher matcher = MIXIN_TARGET.matcher(Files.readString(source));
        while (matcher.find()) {
            String type = matcher.group(1);
            targets.add(type.substring(type.lastIndexOf('.') + 1));
        }
        return targets;
    }

    @Test
    void both_codecs_describe_the_same_types() throws IOException {
        Set<String> jackson3 = mixinTargetsOf(JACKSON3_CODEC);
        Set<String> jackson2 = mixinTargetsOf(JACKSON2_CODEC);

        assertThat(jackson3)
                .as("mixin registrations found - the codec is not described by mixins any more if this is empty")
                .hasSizeGreaterThan(10);

        assertThat(jackson3)
                .as("a type registered by one codec and not the other is read differently once Jackson 3 is added")
                .containsExactlyInAnyOrderElementsOf(jackson2);
    }
}
