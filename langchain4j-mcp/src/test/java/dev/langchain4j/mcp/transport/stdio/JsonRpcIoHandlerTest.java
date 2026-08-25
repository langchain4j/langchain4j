package dev.langchain4j.mcp.transport.stdio;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

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
    void should_read_lines_and_pass_them_to_the_handler() {
        // given
        String input = "{\"jsonrpc\":\"2.0\",\"id\":1}\n{\"jsonrpc\":\"2.0\",\"id\":2}\n";
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<String> received = new ArrayList<>();

        JsonRpcIoHandler handler = new JsonRpcIoHandler(in, out, received::add, false);

        // when
        handler.run();

        // then
        assertThat(received).containsExactly("{\"jsonrpc\":\"2.0\",\"id\":1}", "{\"jsonrpc\":\"2.0\",\"id\":2}");
    }

    @Test
    void a_handler_that_throws_should_not_stop_the_read_loop() {
        // a message the handler cannot process must not strand the subprocess with a dead reader
        String input = "boom\n{\"jsonrpc\":\"2.0\",\"id\":1}\n";
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<String> received = new ArrayList<>();

        JsonRpcIoHandler handler = new JsonRpcIoHandler(
                in,
                out,
                line -> {
                    if (line.equals("boom")) {
                        throw new IllegalStateException("cannot handle this");
                    }
                    received.add(line);
                },
                false);

        // when
        handler.run();

        // then
        assertThat(received).containsExactly("{\"jsonrpc\":\"2.0\",\"id\":1}");
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
    void should_read_multibyte_messages_as_utf8() {
        // given
        String message = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"caf\u00e9 \ud55c\uae00 \ud83d\ude80\"}";
        ByteArrayInputStream in = new ByteArrayInputStream((message + "\n").getBytes(UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<String> received = new ArrayList<>();

        JsonRpcIoHandler handler = new JsonRpcIoHandler(in, out, received::add, false);

        // when
        handler.run();

        // then
        assertThat(received).containsExactly(message);
    }

    @Test
    void should_write_multibyte_messages_as_utf8() throws Exception {
        // given
        String message = "{\"jsonrpc\":\"2.0\",\"id\":1,\"params\":\"caf\u00e9 \ud55c\uae00 \ud83d\ude80\"}";
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonRpcIoHandler handler = new JsonRpcIoHandler(in, out, ignored -> {}, false);

        // when
        handler.submit(message);

        // then
        assertThat(out.toByteArray()).isEqualTo((message + System.lineSeparator()).getBytes(UTF_8));
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
