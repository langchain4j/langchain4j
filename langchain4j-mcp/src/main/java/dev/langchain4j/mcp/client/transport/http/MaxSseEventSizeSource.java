package dev.langchain4j.mcp.client.transport.http;

import java.io.IOException;
import okio.Buffer;
import okio.ForwardingSource;
import okio.Source;

/**
 * Limits the size of one event of an SSE stream. OkHttp buffers an event in memory until a blank line arrives,
 * so without a limit a server could make the client buffer an unbounded amount of data by sending an event that
 * never ends. When an event exceeds the limit, reading fails with an {@link IOException}, which closes the stream.
 */
final class MaxSseEventSizeSource extends ForwardingSource {

    private static final int MESSAGE_EVENT_OVERHEAD = "event: message\r\n".length() + "data: ".length();

    private final int maxMessageSize;
    private final long maxEventBytes;
    private long eventBytes;
    private boolean insideLine;
    private boolean onlyLineFeedEndsLine;
    private boolean afterCarriageReturn;

    MaxSseEventSizeSource(Source delegate, int maxMessageSize) {
        super(delegate);
        this.maxMessageSize = maxMessageSize;
        this.maxEventBytes = (long) maxMessageSize + MESSAGE_EVENT_OVERHEAD;
    }

    @Override
    public long read(Buffer sink, long byteCount) throws IOException {
        long bytesRead = super.read(sink, byteCount);
        if (bytesRead > 0) {
            Buffer received = new Buffer();
            sink.copyTo(received, sink.size() - bytesRead, bytesRead);
            while (!received.exhausted()) {
                count(received.readByte());
            }
        }
        return bytesRead;
    }

    private void count(byte next) throws IOException {
        if (afterCarriageReturn) {
            afterCarriageReturn = false;
            if (next == '\n') {
                return;
            }
        }
        if (endsLine(next)) {
            afterCarriageReturn = next == '\r';
            if (!insideLine) {
                eventBytes = 0;
            }
            insideLine = false;
            return;
        }
        if (!insideLine) {
            insideLine = true;
            // OkHttp reads the values of the event, id and retry fields up to a line feed only,
            // so a carriage return must not end those lines here, or it would look like a blank line
            onlyLineFeedEndsLine = next == 'e' || next == 'i' || next == 'r';
        }
        if (++eventBytes > maxEventBytes) {
            throw new IOException("Message from the MCP server exceeds the maximum size of " + maxMessageSize
                    + " bytes; use HttpMcpTransport.Builder.maxMessageSize to raise the limit");
        }
    }

    private boolean endsLine(byte next) {
        return next == '\n' || (next == '\r' && !(insideLine && onlyLineFeedEndsLine));
    }
}
