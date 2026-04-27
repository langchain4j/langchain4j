package dev.langchain4j.mcp.client.transport.stdio;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ProcessStderrHandlerTest {

    private ProcessBuilder processBuilder;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        // Create a simple process that writes to stderr
        processBuilder = new ProcessBuilder("sh", "-c", "echo 'normal output' >&2; sleep 0.5");

        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ProcessStderrHandler.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        // Ensure DEBUG level is enabled
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
    }

    @Test
    void should_log_stderr_messages_with_correct_prefix() throws Exception {
        // given
        Process process = processBuilder.start();

        // when: run the stderr handler
        ProcessStderrHandler handler = new ProcessStderrHandler(process);
        Thread thread = new Thread(handler);
        thread.start();
        thread.join(3000);

        // then: verify stderr is logged with [MCP stderr] prefix
        List<ILoggingEvent> events = listAppender.list;
        assertThat(events).isNotEmpty();

        // Check that we have the info log at start
        boolean foundInfoLog = events.stream().anyMatch(e -> e.getMessage().contains("[MCP stderr] prefix"));
        assertThat(foundInfoLog).isTrue();

        // Check that stderr output is logged with [MCP stderr] prefix
        boolean foundMcpStderrPrefix =
                events.stream().anyMatch(e -> e.getFormattedMessage().contains("[MCP stderr] normal output"));
        assertThat(foundMcpStderrPrefix).isTrue();

        // Check that we do NOT have [ERROR] prefix (regression check)
        boolean foundErrorPrefix =
                events.stream().anyMatch(e -> e.getFormattedMessage().contains("[ERROR]"));
        assertThat(foundErrorPrefix).isFalse();
    }

    @Test
    void should_not_log_at_error_level_for_normal_stderr_output() throws Exception {
        // given
        processBuilder = new ProcessBuilder("sh", "-c", "echo 'test line 1' >&2; echo 'test line 2' >&2; sleep 0.5");
        Process process = processBuilder.start();

        // when
        ProcessStderrHandler handler = new ProcessStderrHandler(process);
        Thread thread = new Thread(handler);
        thread.start();
        thread.join(3000);

        // then: no ERROR-level logs should appear for normal stderr output
        List<ILoggingEvent> errorLogs = listAppender.list.stream()
                .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.ERROR)
                .toList();
        assertThat(errorLogs).isEmpty();
    }

    @Test
    void should_start_with_info_log_message() throws Exception {
        // given
        Process process = processBuilder.start();

        // when
        ProcessStderrHandler handler = new ProcessStderrHandler(process);
        Thread thread = new Thread(handler);
        thread.start();
        thread.join(3000);

        // then: the first INFO log should be present
        List<ILoggingEvent> infoLogs = listAppender.list.stream()
                .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.INFO)
                .toList();
        assertThat(infoLogs).isNotEmpty();
        assertThat(infoLogs.get(0).getMessage())
                .contains("MCP server stderr messages will be logged with [MCP stderr] prefix");
    }
}
