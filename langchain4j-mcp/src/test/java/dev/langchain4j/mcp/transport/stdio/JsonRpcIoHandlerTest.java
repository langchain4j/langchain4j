package dev.langchain4j.mcp.transport.stdio;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class JsonRpcIoHandlerTest {

    @Test
    void should_read_valid_json_lines_and_pass_to_handler() {
        // given
        String input = "{\"jsonrpc\":\"2.0\",\"id\":1}\n{\"jsonrpc\":\"2.0\",\"id\":2}\n";
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<JsonNode> received = new ArrayList<>();

        JsonRpcIoHandler handler = new JsonRpcIoHandler(in, out, received::add, false);

        // when
        handler.run();

        // then
        assertThat(received).hasSize(2);
        assertThat(received.get(0).get("id").asInt()).isEqualTo(1);
        assertThat(received.get(1).get("id").asInt()).isEqualTo(2);
    }

    @Test
    void should_ignore_invalid_json_lines() {
        // given
        String input = "not json\n{\"jsonrpc\":\"2.0\",\"id\":1}\n{broken\n";
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<JsonNode> received = new ArrayList<>();

        JsonRpcIoHandler handler = new JsonRpcIoHandler(in, out, received::add, false);

        // when
        handler.run();

        // then
        assertThat(received).hasSize(1);
        assertThat(received.get(0).get("id").asInt()).isEqualTo(1);
    }

    @Test
    void should_write_messages_with_line_separator_on_submit() throws Exception {
        // given
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonRpcIoHandler handler = new JsonRpcIoHandler(in, out, ignored -> {}, false);

        // when
        handler.submit("{\"x\":1}");

        // then
        assertThat(out.toString(UTF_8)).isEqualTo("{\"x\":1}" + System.lineSeparator());
    }

    @Test
    void should_stop_reading_after_close() throws Exception {
        // given
        BlockingInputStream in = new BlockingInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonRpcIoHandler handler = new JsonRpcIoHandler(in, out, ignored -> {}, false);

        Thread thread = new Thread(handler);
        thread.start();

        // when: IO thread is blocked on read
        assertThat(in.readStarted.await(1, TimeUnit.SECONDS)).isTrue();

        // and when: handler is closed
        handler.close();
        thread.join(1000);

        // then: reader thread exits
        assertThat(thread.isAlive()).isFalse();
    }

    private static class BlockingInputStream extends InputStream {

        private final CountDownLatch readStarted = new CountDownLatch(1);
        private final CountDownLatch closeCalled = new CountDownLatch(1);

        @Override
        public int read() {
            readStarted.countDown();
            try {
                closeCalled.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return -1;
        }

        @Override
        public void close() throws IOException {
            closeCalled.countDown();
        }
    }
}
