package dev.langchain4j.agentic.patterns.goap.horoscope;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SignExtractor {

    @UserMessage(
            """
            Extract the zodiac sign of a person from the following prompt: {{prompt}}
            """)
    @Agent("Extract a person from user's prompt")
    Sign extractSign(@V("prompt") String prompt);
}
