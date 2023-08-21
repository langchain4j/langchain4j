package dev.langchain4j.store.embedding;

class RequestToMilvusFailedException extends RuntimeException {

    public RequestToMilvusFailedException() {
        super();
    }

    public RequestToMilvusFailedException(String message) {
        super(message);
    }

    public RequestToMilvusFailedException(String message, Throwable cause) {
        super(message, cause);
    }

}
