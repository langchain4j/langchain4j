package dev.langchain4j.test.quarkus;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.quarkus.Models.toolCall;
import static dev.langchain4j.test.quarkus.Models.useChatModelResponding;
import static dev.langchain4j.test.quarkus.Models.useStreamingChatModel;
import static io.restassured.RestAssured.given;

import com.example.support.SupportResource;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.test.service.AiServiceInvocation;
import dev.langchain4j.test.service.AiServiceCapture;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RestResourceTest {

    @Inject
    SupportResource supportResource;

    @Test
    void http_request_reaches_the_assistant() {
        useChatModelResponding(AiMessage.from("Hello from the assistant"));

        String body = given().queryParam("q", "Hi")
                .when()
                .get("/support/chat")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        assertThat(body).isEqualTo("Hello from the assistant");
    }

    @Test
    void does_not_capture_invocation_started_by_http_request() {
        ChatModelMock chatModel = useChatModelResponding(AiMessage.from("Hello from the assistant"));

        AiServiceCapture invocations = AiServiceCapture.capture(() -> given().queryParam("q", "Hi")
                .when()
                .get("/support/chat")
                .then()
                .statusCode(200)
                .extract()
                .asString());

        assertThat(chatModel.requests()).as("the AI Service was called, on a server thread").hasSize(1);
        assertThat(invocations.invocations()).isEmpty();
        assertThatThrownBy(invocations::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("No AI Service invocation was captured.")
                .hasMessageContaining("Invocations started on other threads are not captured")
                .hasMessageContaining("an HTTP request to a running server");
    }

    @Test
    void does_not_capture_invocation_started_by_http_request_to_uni_endpoint() {
        useChatModelResponding(AiMessage.from("Async hello"));

        AiServiceCapture invocations = AiServiceCapture.capture(() -> given().queryParam("q", "Hi")
                .when()
                .get("/support/chat-async")
                .then()
                .statusCode(200)
                .extract()
                .asString());

        assertThat(invocations.invocations()).isEmpty();
    }

    @Test
    void http_request_to_multi_endpoint_streams_the_answer() {
        useStreamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hello ", "world"));

        String body = given().queryParam("q", "Hi")
                .when()
                .get("/support/chat-stream")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        assertThat(body).isEqualTo("Hello world");
    }

    @Test
    void records_invocation_when_resource_bean_is_called_directly() {
        useChatModelResponding(
                toolCall("getOrderStatus", "{\"orderNumber\":\"ORD-1\"}"), AiMessage.from("ORD-1 has shipped."));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> supportResource.chat("Where is ORD-1?"))
                .invocation();

        assertThat(invocation).hasToolExecutedWith("getOrderStatus", Map.of("orderNumber", "ORD-1"));
        assertThat(invocation.outputText()).isEqualTo("ORD-1 has shipped.");
    }

    @Test
    void records_uni_returned_by_resource_bean() {
        useChatModelResponding(AiMessage.from("Async hello"));

        AiServiceInvocation invocation = AiServiceCapture.capture(() -> supportResource.chatAsync("Hi"))
                .invocation();

        assertThat(invocation.outputText()).isEqualTo("Async hello");
    }

    @Test
    void records_multi_returned_by_resource_bean() {
        useStreamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hello ", "world"));

        AiServiceInvocation invocation = AiServiceCapture.capture(() -> supportResource.chatStream("Hi"))
                .invocation();

        assertThat(invocation.outputText()).isEqualTo("Hello world");
    }

    @Test
    void output_is_the_validatable_response_of_rest_assured_but_nothing_is_recorded() {
        ChatModelMock chatModel = useChatModelResponding(AiMessage.from("Hello from the assistant"));

        AiServiceCapture capture = AiServiceCapture.capture(() -> given().queryParam("q", "Hi")
                .when()
                .get("/support/chat")
                .then()
                .statusCode(200));

        ValidatableResponse response = capture.lambdaOutput(ValidatableResponse.class);
        assertThat(response.extract().asString()).isEqualTo("Hello from the assistant");
        assertThat(chatModel.requests()).hasSize(1);
        assertThat(capture.invocations()).as("the AI Service ran on a server thread").isEmpty();
        assertThatThrownBy(capture::outputText)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("an HTTP request to a running server");
        assertThatThrownBy(() -> assertThat(capture).hasSucceeded())
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("No AI Service invocation was captured.");
    }
}
