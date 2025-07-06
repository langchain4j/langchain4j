package dev.langchain4j.model.chat.common

import dev.langchain4j.data.message.UserMessage.userMessage
import dev.langchain4j.exception.AuthenticationException
import dev.langchain4j.exception.HttpException
import dev.langchain4j.exception.InternalServerException
import dev.langchain4j.exception.InvalidRequestException
import dev.langchain4j.exception.LangChain4jException
import dev.langchain4j.exception.ModelNotFoundException
import dev.langchain4j.exception.RateLimitException
import dev.langchain4j.exception.TimeoutException
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import me.kpavlov.aimocks.core.AbstractBuildingStep
import me.kpavlov.aimocks.core.AbstractMockLlm
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val TIMEOUT: Duration = 1.seconds

/**
 * Abstract base test class for validating the behavior of chat models when encountering errors.
 *
 * This class is a part of "Model Compatibility Kit" tests
 * and provides a framework to simulate various error scenarios
 * and ensures that the implementations of `ChatModel` handle those scenarios correctly.
 *
 * It leverages parameterized tests for verifying error handling across multiple HTTP status codes
 * and includes specific tests for timeout scenarios.
 *
 * @param MODEL The type of the chat model being tested.
 * @param MOCK The mock LLM (Large Language Model) type used for simulations in the tests.
 * @constructor Accepts a mock LLM instance required for simulating various scenarios.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
public abstract class AbstractChatModelErrorsTest<
        out MODEL : ChatModel,
        out MOCK : AbstractMockLlm,
        >(
    protected val mock: MOCK
) {

    protected abstract fun createModel(temperature: Double, timeout: Duration? = null): MODEL

    protected abstract fun whenMockMatched(question: String, temperature: Double): AbstractBuildingStep<*, *>

    // language=json
    protected open fun errorResponseBody(message: String): String = ""

    @AfterEach
    public fun afterEach() {
        mock.verifyNoUnmatchedRequests()
    }

    @ParameterizedTest(name = "{argumentSetName}({0}) -> {1}")
    @MethodSource("errors")
    public fun should_handle_error_responses(
        httpStatusCode: Int,
        expectedException: Class<LangChain4jException>
    ) {
        // given
        val temperature = Random.nextDouble(0.1, 1.0) // ⚠️ Must be unique per execution!

        val question = "Return error: $httpStatusCode"
        val message = "Error : $httpStatusCode"

        whenMockMatched(question = question, temperature = temperature)
            .respondsError(responseType = String::class) {
                httpStatus = HttpStatusCode.fromValue(httpStatusCode)
                body = errorResponseBody(message)
            }

        val model = createModel(temperature = temperature)

        // when-then
        assertThatThrownBy {
            model.chat(
                ChatRequest.builder()
                    .temperature(temperature)
                    .messages(userMessage(question))
                    .build()
            )
        }
            .isExactlyInstanceOf(expectedException)
            .satisfies({ ex: Throwable ->
                ex.javaClass shouldBe expectedException
                ex.cause?.javaClass shouldBe HttpException::class.java
                (ex.cause as HttpException).statusCode() shouldBe httpStatusCode
            })
    }

    @Test
    public fun should_handle_timeout() {
        // given
        val temperature = Random.nextDouble(0.1, 1.0) // ⚠️ Must be unique per execution!

        val model = createModel(temperature, timeout = TIMEOUT)

        val question = "Simulate timeout"

        whenMockMatched(question = question, temperature = temperature)
            .respondsError(responseType = String::class) {
                httpStatus = HttpStatusCode.OK
                delay = TIMEOUT * 2
                body = errorResponseBody("timeout")
            }

        // when-then
        assertThatThrownBy {
            model.chat(
                ChatRequest.builder()
                    .temperature(temperature)
                    .messages(userMessage(question))
                    .build()
            )
        }.isExactlyInstanceOf(TimeoutException::class.java)
    }

    protected open fun errors(): List<Arguments> = listOf(
        argumentSet(
            "Bad request",
            400,
            InvalidRequestException::class.java
        ),
        argumentSet(
            "Unauthenticated",
            401,
            AuthenticationException::class.java
        ),
        argumentSet(
            "Unauthorized",
            403,
            AuthenticationException::class.java
        ),
        argumentSet(
            "Not found",
            404,
            ModelNotFoundException::class.java
        ),
        argumentSet(
            "Request entity too large",
            413,
            InvalidRequestException::class.java
        ),
        argumentSet(
            "Too many requests",
            429,
            RateLimitException::class.java
        ),
        argumentSet(
            "Internal server error",
            500,
            InternalServerException::class.java
        ),
        argumentSet(
            "Service unavailable",
            503,
            InternalServerException::class.java
        )
    )
}

