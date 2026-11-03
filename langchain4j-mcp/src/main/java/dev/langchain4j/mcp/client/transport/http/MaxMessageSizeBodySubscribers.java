package dev.langchain4j.mcp.client.transport.http;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Body subscribers that limit how much of a response from the MCP server is buffered in memory,
 * so that a server cannot make the client buffer an unbounded amount of data by sending a message that never ends.
 * They replace {@link HttpResponse.BodySubscribers#fromLineSubscriber(Flow.Subscriber)} and
 * {@link HttpResponse.BodySubscribers#ofString(java.nio.charset.Charset)}, which buffer a line or a body of any size.
 */
final class MaxMessageSizeBodySubscribers {

    private static final Logger LOG = LoggerFactory.getLogger(StreamableHttpMcpTransport.class);
    private static final int SSE_DATA_FIELD_NAME_LENGTH = "data:".length();

    private MaxMessageSizeBodySubscribers() {}

    /**
     * Splits the response into lines, as {@link HttpResponse.BodySubscribers#fromLineSubscriber(Flow.Subscriber)} does
     * (a line ends with LF, CR or CRLF), and passes each line on to {@code downstream}. Each line of an SSE stream
     * carries one message, so a line can hold at most {@code maxMessageSize} bytes after its {@code data:} field name.
     * When a line exceeds that, the stream is closed and {@code downstream} receives an {@link IllegalStateException}.
     * <p>
     * Lines are passed on as they are read rather than on demand: the subscribers of this transport request
     * one more line for each line they receive.
     */
    static HttpResponse.BodySubscriber<Void> lines(Flow.Subscriber<String> downstream, int maxMessageSize) {
        return new LineSubscriber(downstream, maxMessageSize);
    }

    /**
     * Collects the whole response as a UTF-8 string. When it exceeds {@code maxMessageSize} bytes, the stream is closed
     * and the body fails with an {@link IllegalStateException}.
     */
    static HttpResponse.BodySubscriber<String> string(int maxMessageSize) {
        return new StringSubscriber(maxMessageSize);
    }

    private static IllegalStateException messageTooLarge(int maxMessageSize) {
        String message = "Message from the MCP server exceeds the maximum size of " + maxMessageSize
                + " bytes; use StreamableHttpMcpTransport.Builder.maxMessageSize to raise the limit";
        LOG.warn("Closing the stream: {}", message);
        return new IllegalStateException(message);
    }

    private static final class LineSubscriber implements HttpResponse.BodySubscriber<Void> {

        private final Flow.Subscriber<String> downstream;
        private final int maxMessageSize;
        private final long maxLineBytes;
        private final CompletableFuture<Void> body = new CompletableFuture<>();
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();
        private volatile Flow.Subscription subscription;
        private volatile boolean done;
        private boolean afterCr;

        private LineSubscriber(Flow.Subscriber<String> downstream, int maxMessageSize) {
            this.downstream = downstream;
            this.maxMessageSize = maxMessageSize;
            this.maxLineBytes = (long) maxMessageSize + SSE_DATA_FIELD_NAME_LENGTH;
        }

        @Override
        public CompletionStage<Void> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            downstream.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    // lines are passed on as they are read, see lines()
                }

                @Override
                public void cancel() {
                    done = true;
                    subscription.cancel();
                    body.complete(null);
                }
            });
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                while (buffer.hasRemaining()) {
                    if (done) {
                        return;
                    }
                    byte next = buffer.get();
                    if (afterCr) {
                        afterCr = false;
                        if (next == '\n') {
                            continue;
                        }
                    }
                    if (next == '\n' || next == '\r') {
                        afterCr = next == '\r';
                        emitLine();
                    } else if (line.size() >= maxLineBytes) {
                        fail();
                        return;
                    } else {
                        line.write(next);
                    }
                }
            }
            if (!done) {
                subscription.request(1);
            }
        }

        private void emitLine() {
            String text = line.toString(UTF_8);
            line.reset();
            downstream.onNext(text);
        }

        private void fail() {
            done = true;
            line.reset();
            subscription.cancel();
            downstream.onError(messageTooLarge(maxMessageSize));
            body.complete(null);
        }

        @Override
        public void onError(Throwable throwable) {
            if (done) {
                return;
            }
            done = true;
            downstream.onError(throwable);
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            if (line.size() > 0) {
                emitLine();
            }
            downstream.onComplete();
            body.complete(null);
        }
    }

    private static final class StringSubscriber implements HttpResponse.BodySubscriber<String> {

        private final int maxMessageSize;
        private final CompletableFuture<String> body = new CompletableFuture<>();
        private final ByteArrayOutputStream received = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        private boolean done;

        private StringSubscriber(int maxMessageSize) {
            this.maxMessageSize = maxMessageSize;
        }

        @Override
        public CompletionStage<String> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (done) {
                return;
            }
            for (ByteBuffer buffer : buffers) {
                if ((long) received.size() + buffer.remaining() > maxMessageSize) {
                    done = true;
                    received.reset();
                    subscription.cancel();
                    body.completeExceptionally(messageTooLarge(maxMessageSize));
                    return;
                }
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                received.writeBytes(bytes);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            if (!done) {
                done = true;
                body.completeExceptionally(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (!done) {
                done = true;
                body.complete(received.toString(UTF_8));
            }
        }
    }
}
