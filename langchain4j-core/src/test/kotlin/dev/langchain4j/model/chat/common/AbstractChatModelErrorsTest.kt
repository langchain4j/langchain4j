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
import io.ktor.http.HttpStatusCode
import me.kpavlov.aimocks.core.AbstractBuildingStep
import me.kpavlov.aimocks.core.AbstractMockLlm
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.of
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val TIMEOUT: Duration = 1.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
public abstract class AbstractChatModelErrorsTest<
        out MODEL : ChatModel,
        out MOCK : AbstractMockLlm,
        >(
    protected val mock: MOCK
) {

    protected var temperature: Double = -1.0

    protected abstract fun createModel(temperature: Double, timeout: Duration? = null): MODEL

    protected abstract fun whenMockMatched(question: String, temperature: Double): AbstractBuildingStep<*, *>

    // language=json
    protected open fun errorResponseBody(message: String): String = ""

    @BeforeEach
    public fun beforeEach() {
        temperature = Random.nextDouble(0.1, 1.0)
    }

    @ParameterizedTest
    @MethodSource("errors")
    public fun should_handle_error_responses(
        httpStatusCode: Int,
        exception: Class<LangChain4jException>
    ) {
        // given

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
            .isExactlyInstanceOf(exception)
            .satisfies({ ex: Throwable ->
                assertThat((ex.cause as HttpException).statusCode())
                    .`as`("statusCode")
                    .isEqualTo(httpStatusCode)
            })
    }

    @Test
    public fun should_handle_timeout() {
        // given
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

    @AfterEach
    public fun afterEach() {
        mock.verifyNoUnmatchedRequests()
    }

    protected open fun errors(): Stream<Arguments?> {
        return Stream.of<Arguments?>(
            of(
                400,
                InvalidRequestException::class.java
            ),
            of(
                401,
                AuthenticationException::class.java
            ),
            of(
                403,
                AuthenticationException::class.java
            ),
            of(
                404,
                ModelNotFoundException::class.java
            ),
            of(
                413,
                InvalidRequestException::class.java
            ),
            of(
                429,
                RateLimitException::class.java
            ),
            of(
                500,
                InternalServerException::class.java
            ),
            of(
                503,
                InternalServerException::class.java
            )
        )
    }
}
