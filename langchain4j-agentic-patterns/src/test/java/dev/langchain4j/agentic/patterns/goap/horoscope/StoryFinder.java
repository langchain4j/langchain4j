package dev.langchain4j.agentic.patterns.goap.horoscope;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface StoryFinder {

    @SystemMessage(
            """
                You're a story finder, use the provided web search tools, calling it once and only once,
                to find a fictional and funny story on the internet about the user provided topic.
                """)
    @UserMessage(
            """
                Find a story on the internet for {{person}} who has the following horoscope: {{horoscope}}.
                """)
    @Agent("Find a story on the internet for a given person with a given horoscope")
    String findStory(@V("person") Person person, @V("horoscope") String horoscope);
}
