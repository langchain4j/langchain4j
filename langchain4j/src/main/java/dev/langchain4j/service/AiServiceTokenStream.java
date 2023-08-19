package dev.langchain4j.service;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;
import java.util.function.Consumer;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

class AiServiceTokenStream implements TokenStream {

    private final List<ChatMessage> messagesToSend;
    private final AiServiceContext context;
    private final Object memoryId;

    AiServiceTokenStream(List<ChatMessage> messagesToSend, AiServiceContext context, Object memoryId) {
        this.messagesToSend = ensureNotEmpty(messagesToSend, "messagesToSend");
        this.context = ensureNotNull(context, "context");
        this.memoryId = ensureNotNull(memoryId, "memoryId");;
        ensureNotNull(context.streamingChatLanguageModel, "streamingChatLanguageModel");
    }

    @Override
    public OnCompleteOrOnError onNext(Consumer<String> tokenHandler) {

        return new OnCompleteOrOnError() {

            @Override
            public OnError onComplete(Runnable completionHandler) {

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
        private final Runnable completionHandler;
        private final Consumer<Throwable> errorHandler;

        private AiServiceOnStart(Consumer<String> tokenHandler,
                                 Runnable completionHandler,
                                 Consumer<Throwable> errorHandler) {
            this.tokenHandler = ensureNotNull(tokenHandler, "tokenHandler");
            this.completionHandler = completionHandler;
            this.errorHandler = errorHandler;
        }

        @Override
        public void start() {

            context.streamingChatLanguageModel.sendMessages(
                    messagesToSend,
                    context.toolSpecifications,
                    new AiServiceStreamingResponseHandler(
                            context,
                            memoryId,
                            tokenHandler,
                            completionHandler,
                            errorHandler
                    )
            );
        }
    }
}
