package dev.langchain4j.jackson3;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.spi.data.message.ChatMessageJsonCodecFactory;
import dev.langchain4j.spi.json.JsonCodecFactory;
import dev.langchain4j.spi.json.ProviderJsonCodecFactory;
import dev.langchain4j.spi.json.StateJsonCodecFactory;
import java.util.Collection;
import org.junit.jupiter.api.Test;

/**
 * Asserts that the Jackson 3 codecs are the ones the SPI actually resolves to.
 *
 * <p>The {@code jackson3} Maven profile only puts this module on a module's test classpath. Whether
 * the SPI then resolves to it is a separate question, and a yes is the whole point of the profile.
 * If the answer quietly became no - a services file lost in a refactor, a stale artifact, a change
 * in factory ordering - every profiled module would keep passing, on Jackson 2, and the run would
 * look like coverage it is not. So this runs in each of those modules through surefire's
 * dependenciesToScan, not only here.
 *
 * <p>Each case asserts the first factory, because that is what every call site takes.
 */
class Jackson3ActiveTest {

    @Test
    void the_general_codec_resolves_to_jackson3() {
        assertThat(first(JsonCodecFactory.class)).isInstanceOf(Jackson3JsonCodecFactory.class);
    }

    @Test
    void the_provider_codec_resolves_to_jackson3() {
        assertThat(first(ProviderJsonCodecFactory.class)).isInstanceOf(Jackson3ProviderJsonCodecFactory.class);
    }

    @Test
    void the_chat_message_codec_resolves_to_jackson3() {
        assertThat(first(ChatMessageJsonCodecFactory.class)).isInstanceOf(Jackson3ChatMessageJsonCodecFactory.class);
    }

    @Test
    void the_agent_state_codec_resolves_to_jackson3() {
        assertThat(first(StateJsonCodecFactory.class)).isInstanceOf(Jackson3StateJsonCodecFactory.class);
    }

    private static <T> Object first(Class<T> spi) {
        Collection<T> factories = loadFactories(spi);
        assertThat(factories)
                .as("No %s on the classpath: langchain4j-core-jackson3 is not resolving here, "
                        + "so this module's tests are running on Jackson 2", spi.getSimpleName())
                .isNotEmpty();
        return factories.iterator().next();
    }
}
