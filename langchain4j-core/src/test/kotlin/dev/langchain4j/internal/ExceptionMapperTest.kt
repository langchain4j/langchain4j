package dev.langchain4j.internal

import dev.langchain4j.exception.AuthenticationException
import dev.langchain4j.exception.HttpException
import dev.langchain4j.exception.InternalServerException
import dev.langchain4j.exception.InvalidRequestException
import dev.langchain4j.exception.LangChain4jException
import dev.langchain4j.exception.ModelNotFoundException
import dev.langchain4j.exception.RateLimitException
import dev.langchain4j.exception.TimeoutException
import dev.langchain4j.exception.UnresolvedModelServerException
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.SocketTimeoutException
import java.nio.channels.UnresolvedAddressException
import java.util.concurrent.Callable
import java.util.function.Function
import java.util.stream.Stream

@Suppress("TooGenericExceptionThrown")
internal class ExceptionMapperTest {

    private val subject = ExceptionMapper.DefaultExceptionMapper()

    companion object {
        @JvmStatic
        fun errors(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(400, InvalidRequestException::class.java),
                Arguments.of(401, AuthenticationException::class.java),
                Arguments.of(403, AuthenticationException::class.java),
                Arguments.of(404, ModelNotFoundException::class.java),
                Arguments.of(408, TimeoutException::class.java),
                Arguments.of(413, InvalidRequestException::class.java),
                Arguments.of(429, RateLimitException::class.java),
                Arguments.of(500, InternalServerException::class.java),
                Arguments.of(503, InternalServerException::class.java)
            )
        }

        @JvmStatic
        fun specificExceptions(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(UnresolvedAddressException(), UnresolvedModelServerException::class.java),
                Arguments.of(SocketTimeoutException("Connection timed out"), TimeoutException::class.java)
            )
        }

    }

    @Test
    internal fun `Should map exception using static method`() {
        // given
        val action = Callable<String> { throw RuntimeException("Test exception") }

        // when/then
        val exception = assertThrows<RuntimeException> {
            ExceptionMapper.mappingException(action)
        }

        exception.message shouldBe "Test exception"
    }

    @Test
    internal fun `Should execute action without exception`() {
        // given
        val action = Callable { "Success" }

        // when
        val result = ExceptionMapper.DEFAULT.withExceptionMapper(action)

        // then
        result shouldBe "Success"
    }

    @Test
    internal fun `Should map exception when action throws`() {
        // given
        val expectedException = RuntimeException("Test exception")
        val action = Callable<String> { throw expectedException }

        // when/then
        val exception = assertThrows<RuntimeException> {
            ExceptionMapper.DEFAULT.withExceptionMapper(action)
        }

        exception.message shouldBe "Test exception"
        exception shouldBe expectedException
    }

    @Test
    internal fun `Should use custom httpStatusCodeExtractor`() {
        // given
        val extractor = Function<Throwable, Int> { 404 }
        val mapper = ExceptionMapper.DefaultExceptionMapper(extractor)
        val exception = RuntimeException("Test exception")

        // when
        val mappedException = mapper.mapException(exception)

        // then
        mappedException.shouldBeInstanceOf<ModelNotFoundException>()
    }

    @Test
    internal fun `Should extract status code from HttpException`() {
        // given
        val httpException = HttpException(404, "Not found")
        val wrappedException = RuntimeException(httpException)

        // when
        val mappedException = subject.mapException(wrappedException)

        // then
        mappedException.shouldBeInstanceOf<ModelNotFoundException>()
        mappedException.cause shouldBeSameInstanceAs wrappedException
    }

    @ParameterizedTest
    @MethodSource("specificExceptions")
    internal fun `Should map specific exceptions to appropriate types`(
        exception: Exception,
        expectedType: Class<out LangChain4jException>
    ) {
        // when
        val mappedException = subject.mapException(exception)

        // then
        mappedException.javaClass shouldBe expectedType
    }

    @Test
    internal fun `Should return original exception if it's a RuntimeException`() {
        // given
        val exception = RuntimeException("Test exception")

        // when
        val mappedException = subject.mapException(exception)

        // then
        mappedException shouldBe exception
    }

    @Test
    internal fun `Should wrap checked exception in LangChain4jException`() {
        // given
        val exception = Exception("Test exception")

        // when
        val mappedException = subject.mapException(exception)

        // then
        mappedException.shouldBeInstanceOf<LangChain4jException>()
        mappedException.cause shouldBe exception
    }

    @ParameterizedTest
    @MethodSource("errors")
    internal fun `Should handle error responses`(httpStatusCode: Int, exceptionClass: Class<out LangChain4jException>) {
        // given
        val exception = RuntimeException("Test exception")

        // when
        val mappedException = subject.mapHttpStatusCode(exception, httpStatusCode)

        // then
        mappedException.javaClass shouldBe exceptionClass
        mappedException.cause shouldBe exception
    }

    @Test
    internal fun `Should return original exception for unknown status code`() {
        // given
        val exception = RuntimeException("Unknown error")

        // when
        val mappedException = subject.mapHttpStatusCode(exception, 600)

        // then
        mappedException shouldBe exception
        mappedException.cause shouldBe null
    }

    @Test
    internal fun `Should wrap checked exception for unknown status code`() {
        // given
        val exception = Exception("Unknown error")

        // when
        val mappedException = subject.mapHttpStatusCode(exception, 600)

        // then
        mappedException.shouldBeInstanceOf<LangChain4jException>()
        mappedException.cause shouldBe exception
    }
}
