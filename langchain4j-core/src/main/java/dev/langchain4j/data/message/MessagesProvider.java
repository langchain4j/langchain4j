package dev.langchain4j.data.message;

import java.util.Optional;
import java.util.function.Supplier;

public interface MessagesProvider {

    Optional<String> systemMessage();

    Optional<String> userMessage();

    class Dummy implements MessagesProvider {

        public static final MessagesProvider INSTANCE = new Dummy();

        @Override
        public Optional<String> systemMessage() {
            return Optional.empty();
        }

        @Override
        public Optional<String> userMessage() {
            return Optional.empty();
        }
    }

    class SystemMessageDecorator implements MessagesProvider {
        private final MessagesProvider decorated;
        private final Supplier<String> systemMessageSupplier;

        public SystemMessageDecorator(MessagesProvider decorated, Supplier<String> systemMessageSupplier) {
            this.decorated = decorated;
            this.systemMessageSupplier = systemMessageSupplier;
        }

        @Override
        public Optional<String> systemMessage() {
            return Optional.ofNullable(systemMessageSupplier.get());
        }

        @Override
        public Optional<String> userMessage() {
            return decorated.userMessage();
        }
    }

    class UserMessageDecorator implements MessagesProvider {
        private final MessagesProvider decorated;
        private final Supplier<String> userMessageSupplier;

        public UserMessageDecorator(MessagesProvider decorated, Supplier<String> systemMessageSupplier) {
            this.decorated = decorated;
            this.userMessageSupplier = systemMessageSupplier;
        }

        @Override
        public Optional<String> userMessage() {
            return Optional.ofNullable(userMessageSupplier.get());
        }

        @Override
        public Optional<String> systemMessage() {
            return decorated.systemMessage();
        }
    }
}
