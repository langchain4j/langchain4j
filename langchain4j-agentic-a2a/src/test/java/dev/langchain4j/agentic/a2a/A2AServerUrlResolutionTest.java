package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.A2AServerUrlSupplier;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;

class A2AServerUrlResolutionTest {

    public interface A2AWithNoUrl {

        @A2AClientAgent(outputKey = "story")
        String generateStory(@V("topic") String topic);
    }

    public interface A2AWithBothUrlAndSupplier {

        @A2AClientAgent(a2aServerUrl = "http://localhost:8080", outputKey = "story")
        String generateStory(@V("topic") String topic);

        @A2AServerUrlSupplier
        static String serverUrl() {
            return "http://localhost:8080";
        }
    }

    @Test
    void should_fail_when_neither_url_nor_supplier_is_provided() {
        assertThatThrownBy(() ->
                AgenticServices.createAgenticSystem(A2AWithNoUrl.class, (ChatModel) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@A2AServerUrlSupplier");
    }

    @Test
    void should_fail_when_both_url_and_supplier_are_provided() {
        assertThatThrownBy(() ->
                AgenticServices.createAgenticSystem(A2AWithBothUrlAndSupplier.class, (ChatModel) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not both");
    }
}
