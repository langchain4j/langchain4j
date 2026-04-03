package dev.langchain4j.agentic.patterns.goap.horoscope;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface HoroscopeGenerator {
    @SystemMessage(
            """
            You are an astrologist that generates horoscopes based on the user's name and zodiac sign.
            """)
    @UserMessage("""
                Generate the horoscope for {{person}} who is a {{sign}}.
                """)
    @Agent("An astrologist that generates horoscopes based on the user's name and zodiac sign.")
    String horoscope(@V("person") Person person, @V("sign") Sign sign);
}
