package dev.langchain4j.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class AiServiceTokenStream implements TokenStream {

    private final List<ChatMessage> messagesToSend;
    private final AiServiceContext context;
    private final Object memoryId;

    public AiServiceTokenStream(List<ChatMessage> messagesToSend, AiServiceContext context, Object memoryId) {
        this.messagesToSend = ensureNotEmpty(messagesToSend, "messagesToSend");
        this.context = ensureNotNull(context, "context");
        this.memoryId = ensureNotNull(memoryId, "memoryId");
        ensureNotNull(context.streamingChatModel, "streamingChatModel");
    }

    @Override
    public OnCompleteOrOnError onNext(Consumer<String> tokenHandler) {

        return new OnCompleteOrOnError() {

            @Override
            public OnError onComplete(Consumer<Response<AiMessage>> completionHandler) {

                return new OnError() {

                    @Override
                    public OnStart onError(Consumer<Throwable> errorHandler) {
                        return new AiServiceOnStart(tokenHandler, completionHandler, errorHandler);
                    }

                    @Override
                    public OnStart ignoreErrors() {
                        return new AiServiceOnStart(tokenHandler, completionHandler, null);
                    }
                };
            }

            @Override
            public OnStart onError(Consumer<Throwable> errorHandler) {
                return new AiServiceOnStart(tokenHandler, null, errorHandler);
            }

            @Override
            public OnStart ignoreErrors() {
                return new AiServiceOnStart(tokenHandler, null, null);
            }
        };
    }

    private class AiServiceOnStart implements OnStart {

        private final Consumer<String> tokenHandler;
        private final Consumer<Response<AiMessage>> completionHandler;
        private final Consumer<Throwable> errorHandler;

        private AiServiceOnStart(Consumer<String> tokenHandler,
                                 Consumer<Response<AiMessage>> completionHandler,
                                 Consumer<Throwable> errorHandler) {
            this.tokenHandler = ensureNotNull(tokenHandler, "tokenHandler");
            this.completionHandler = completionHandler;
            this.errorHandler = errorHandler;
        }

        @Override
        public void start() {

            AiServiceStreamingResponseHandler handler = new AiServiceStreamingResponseHandler(
                    context,
                    memoryId,
                    initTempToolExecutionMessages(context, messagesToSend),
                    tokenHandler,
                    completionHandler,
                    errorHandler,
                    new TokenUsage()
            );

            if (context.toolSpecifications != null) {
                context.streamingChatModel.generate(messagesToSend, context.toolSpecifications, handler);
            } else {
                context.streamingChatModel.generate(messagesToSend, handler);
            }
        }

        /**
         * Initialize temporary tool execution messages.
         * If context has chat memory, the temp messages list is useless.
         *
         * @param context        the AI service context.
         * @param messagesToSend the init list of messages to send.
         * @return the list of temporary tool execution messages.
         */
        private List<ChatMessage> initTempToolExecutionMessages(AiServiceContext context, List<ChatMessage> messagesToSend) {
            if (context.hasChatMemory()) {
                return Collections.emptyList();
            } else {
                return new ArrayList<>(messagesToSend);
            }
        }
    }
}
