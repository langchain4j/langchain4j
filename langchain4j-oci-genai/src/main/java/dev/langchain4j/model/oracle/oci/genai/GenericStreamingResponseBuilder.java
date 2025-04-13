package dev.langchain4j.model.oracle.oci.genai;

import com.oracle.bmc.generativeaiinference.model.AssistantMessage;
import com.oracle.bmc.generativeaiinference.model.ChatChoice;
import com.oracle.bmc.generativeaiinference.model.FunctionCall;
import com.oracle.bmc.generativeaiinference.model.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class GenericStreamingResponseBuilder {
    private final List<ChatChoice> choiceParts = new ArrayList<>();
    private final String modelName;

    GenericStreamingResponseBuilder(String modelName) {
        this.modelName = modelName;
    }

    void append(ChatChoice partialChoice) {
        if (partialChoice == null) {
            return;
        }

        Integer index = partialChoice.getIndex();
        choiceParts.add(partialChoice);
    }

    ChatResponse build() {
        ChatChoice chatChoice = mergeChoiceParts(choiceParts);
        return OciGenAiChatModel.map(chatChoice, modelName);
    }

    /**
     * data: {"index":0,"message":{"role":"ASSISTANT",  "content":[{"type":"TEXT",    "text":""                                                          }]},"pad":"aa"}
     * data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","id":"chatcmpl-tool-e78c012be89a4742a6ba7e4b0a05b0f2","name":"sqrt"}]},"pad":"aaaaaa"}
     * data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":"{\"arg0\": \""                                        }]},"pad":"aaaaaa"}
     * data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":"16\"}"                                                }]},"pad":"aaaaaaaaa"}
     * data: {          "message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":""                                                     }]},"finishReason":"tool_calls","pad":"aaa"}
     */
    private ChatChoice mergeChoiceParts(List<ChatChoice> choiceParts) {
        StringBuilder finishReason = new StringBuilder();
        StringBuilder content = new StringBuilder();
        StringBuilder toolName = new StringBuilder();
        StringBuilder toolArgs = new StringBuilder();
        StringBuilder toolCallId = new StringBuilder();
        for (ChatChoice chatChoice : choiceParts) {
            if (chatChoice.getFinishReason() != null) {
                finishReason.append(chatChoice.getFinishReason());
            }

            var message = chatChoice.getMessage();
            if (message instanceof AssistantMessage assistantMessage) {
                var contents =
                        Optional.ofNullable(assistantMessage.getContent()).orElseGet(List::of);
                for (var cont : contents) {
                    if (cont instanceof com.oracle.bmc.generativeaiinference.model.TextContent textContent) {
                        content.append(textContent.getText());
                    } else {
                        throw new IllegalStateException(
                                "Only TextContent is supported in streaming chat but got " + cont);
                    }
                }
                var toolCalls =
                        Optional.ofNullable(assistantMessage.getToolCalls()).orElseGet(List::of);
                if (toolCalls.size() == 1) {
                    FunctionCall toolCall = (FunctionCall) toolCalls.get(0);
                    if (toolCall.getName() != null) {
                        toolName.append(toolCall.getName());
                    }
                    if (toolCall.getArguments() != null) {
                        toolArgs.append(toolCall.getArguments());
                    }
                    if (toolCall.getId() != null) {
                        toolCallId.append(toolCall.getId());
                    }
                } else if (toolCalls.size() > 1) {
                    throw new IllegalStateException(
                            "Only single tool-call per response is supported in streaming chat.");
                }
            }
        }

        var resultBuilder = ChatChoice.builder();
        if (!finishReason.isEmpty()) {
            resultBuilder.finishReason(finishReason.toString());
        }

        if (!content.isEmpty()) {
            resultBuilder
                    .message(UserMessage.builder()
                            .content(List.of(com.oracle.bmc.generativeaiinference.model.TextContent.builder()
                                    .text(content.toString())
                                    .build()))
                            .build())
                    .build();
        }

        if (!toolName.isEmpty()) {
            var args = toolArgs.isEmpty() ? "{}" : toolArgs.toString();
            resultBuilder.message(AssistantMessage.builder()
                    .content(List.of(com.oracle.bmc.generativeaiinference.model.TextContent.builder()
                            .text(content.toString())
                            .build()))
                    .toolCalls(List.of(FunctionCall.builder()
                            .id(toolCallId.toString())
                            .name(toolName.toString())
                            .arguments(args)
                            .build()))
                    .build());
        }

        return resultBuilder.build();
    }
}
