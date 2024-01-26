package dev.langchain4j.rag.content.injector;

import dev.langchain4j.MightChangeInTheFuture;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;

import java.util.List;

/**
 * Injects given {@link Content}s into a given {@link UserMessage}.
 * <br>
 * The goal is to format and incorporate the {@link Content}s into the original {@link UserMessage}
 * enabling the LLM to utilize it for generating a grounded response.
 *
 * @see DefaultContentInjector
 */
@MightChangeInTheFuture("This is an experimental feature. Time will tell if this is the right abstraction.")
public interface ContentInjector {

    /**
     * Injects given {@link Content}s into a given {@link UserMessage}.
     *
     * @param contents    The list of {@link Content} to be injected.
     * @param userMessage The {@link UserMessage} into which the {@link Content}s are to be injected.
     * @return The {@link UserMessage} with the injected {@link Content}s.
     */
    UserMessage inject(List<Content> contents, UserMessage userMessage);
}
