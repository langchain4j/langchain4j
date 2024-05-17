package dev.langchain4j.rag.content.injector;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;

import java.util.List;

import static dev.langchain4j.internal.Exceptions.runtime;

/**
 * Injects given {@link Content}s into a given {@link UserMessage}.
 * <br>
 * The goal is to format and incorporate the {@link Content}s into the original {@link UserMessage}
 * enabling the LLM to utilize it for generating a grounded response.
 *
 * @see DefaultContentInjector
 */
@Experimental
public interface ContentInjector {

    /**
     * Injects given {@link Content}s into a given {@link ChatMessage}.
     * <br>
     * This method has a default implementation in order to <b>temporarily</b> support
     * current custom implementations of {@code ContentInjector}. The default implementation will be removed soon.
     *
     * @param contents    The list of {@link Content} to be injected.
     * @param chatMessage The {@link ChatMessage} into which the {@link Content}s are to be injected.
     *                    Can be either a {@link UserMessage} or a {@link SystemMessage}.
     * @return The {@link UserMessage} with the injected {@link Content}s.
     */
    default ChatMessage inject(List<Content> contents, ChatMessage chatMessage) {

        if (!(chatMessage instanceof UserMessage)) {
            throw runtime("Please implement 'ChatMessage inject(List<Content>, ChatMessage)' method " +
                    "in order to inject contents into " + chatMessage);
        }

        return inject(contents, (UserMessage) chatMessage);
    }

    /**
     * Injects given {@link Content}s into a given {@link UserMessage}.
     *
     * @param contents    The list of {@link Content} to be injected.
     * @param userMessage The {@link UserMessage} into which the {@link Content}s are to be injected.
     * @return The {@link UserMessage} with the injected {@link Content}s.
     * @deprecated Use/implement {@link #inject(List, ChatMessage)} instead.
     */
    @Deprecated
    UserMessage inject(List<Content> contents, UserMessage userMessage);
}
