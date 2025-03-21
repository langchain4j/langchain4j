package dev.langchain4j.internal

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.json.shouldEqualSpecifiedJson
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

internal class JacksonJsonCodecTest {
    internal data class ServiceResponse(
        val status: String,
        @JsonProperty("is_success")
        val isSuccess: Boolean
    )

    private val subject = JacksonJsonCodec()

    @Test
    fun `Should create with provided ObjectMapper`() {
        val providedMapper: ObjectMapper = mock()
        val codec = JacksonJsonCodec(providedMapper)
        codec.objectMapper shouldBeSameInstanceAs providedMapper
        verifyNoInteractions(providedMapper)
    }

    @Test
    fun `Should find and register modules on startup`() {
        val registeredModuleIds = subject.objectMapper.registeredModuleIds
        registeredModuleIds shouldContain "com.fasterxml.jackson.module.kotlin.KotlinModule"
        registeredModuleIds shouldContain "langchain4j-module" // custom module
    }

    @Test
    fun `Should deserialize Kotlin data classes`() {
        val result =
            subject.fromJson(
                // language=json
                """{"status": "ok", "is_success": true}""",
                ServiceResponse::class.java
            )
        result shouldBe ServiceResponse("ok", true)
    }

    @Test
    fun `Should serialize Kotlin data classes`() {
        val jsonString = subject.toJson(ServiceResponse("ok", true))
        // language=json
        jsonString shouldEqualSpecifiedJson
            """
            {
              "status" : "ok",
              "is_success" : true
            }
            """.trimIndent()
    }
}
