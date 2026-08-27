package dev.langchain4j.model.watsonx.it;

import static dev.langchain4j.MockitoUtils.ignoreInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.mockito.InOrder;

/**
 * Verifies the callbacks of a single tool call without assuming how the model splits the tool arguments across the
 * partial callbacks. Every model chunks them differently, and some send the whole JSON in a single callback.
 */
final class WatsonxToolCallbacksVerifier {

    static void verifyToolCall(
            StreamingChatResponseHandler handler, InOrder io, int index, String id, String name, String arguments) {

        // Some models talk before calling a tool, so the partial responses are ignored.
        ignoreInteractions(handler).onPartialResponse(any());
        ignoreInteractions(handler).onPartialResponse(any(), any());

        // At least one partial callback must carry the index, the id and the name of the tool, and it must arrive
        // before the complete callback of the same tool.
        io.verify(handler, atLeastOnce())
                .onPartialToolCall(
                        argThat(partial ->
                                partial.index() == index && id.equals(partial.id()) && name.equals(partial.name())),
                        any());

        // The number of partial callbacks depends on how the model chunks the arguments, so the ones that the
        // verification above did not consume are ignored to keep "verifyNoMoreInteractions" happy.
        ignoreInteractions(handler).onPartialToolCall(any());
        ignoreInteractions(handler).onPartialToolCall(any(), any());

        io.verify(handler).onCompleteToolCall(argThat(complete -> {
            ToolExecutionRequest request = complete.toolExecutionRequest();
            return complete.index() == index
                    && id.equals(request.id())
                    && name.equals(request.name())
                    && withoutWhitespace(arguments).equals(withoutWhitespace(request.arguments()));
        }));
    }

    /**
     * The models do not agree on how to indent the tool arguments, so they are compared without any whitespace.
     */
    private static String withoutWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s", "");
    }

    private WatsonxToolCallbacksVerifier() {}
}
