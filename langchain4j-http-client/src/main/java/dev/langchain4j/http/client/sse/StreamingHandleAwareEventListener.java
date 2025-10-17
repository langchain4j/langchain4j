//package dev.langchain4j.http.client.sse;
//
//import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
//
//import dev.langchain4j.http.client.SuccessfulHttpResponse;
//import dev.langchain4j.model.chat.response.StreamingHandle;
//
///**
// * TODO why?
// */
//public class StreamingHandleAwareEventListener implements ServerSentEventListener {
//
//    private final ServerSentEventListener delegate;
//    private final StreamingHandle handle;
//
//    public StreamingHandleAwareEventListener(ServerSentEventListener delegate, StreamingHandle handle) {
//        this.delegate = ensureNotNull(delegate, "delegate");
//        this.handle = ensureNotNull(handle, "handle");
//    }
//
//    @Override
//    public void onOpen(SuccessfulHttpResponse response) {
//        delegate.onOpen(response);
//    }
//
//    @Override
//    public void onEvent(ServerSentEvent event) {
//        delegate.onEvent(event, handle); // TODO explain
//    }
//
//    @Override
//    public void onEvent(ServerSentEvent event, StreamingHandle handle) {
//        delegate.onEvent(event, handle);
//    }
//
//    @Override
//    public void onError(Throwable throwable) {
//        delegate.onError(throwable);
//    }
//
//    @Override
//    public void onClose() {
//        delegate.onClose();
//    }
//}
