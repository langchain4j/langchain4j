package dev.langchain4j.kotlin.data.document

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotSameInstanceAs
import dev.langchain4j.data.document.Metadata
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldNotContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class MergeMetadataTest {
    @Test
    fun `merge should combine unique metadata from both objects`() {
        val metadata1 =
            Metadata.from(
                mapOf("key1" to "value1", "key2" to "value2")
            )

        val metadata2 =
            Metadata.from(
                mapOf("key3" to "value3", "key4" to "value4")
            )

        val result = metadata1.merge(metadata2)

        result.toMap() shouldContainExactly
                mapOf(
                    "key1" to "value1",
                    "key2" to "value2",
                    "key3" to "value3",
                    "key4" to "value4"
                )

    }

    @Test
    fun `merge should combine with null object`() {
        val metadata =
            Metadata.from(
                mapOf("key1" to "value1", "key2" to "value2")
            )

        val result = metadata.merge(null)

        result shouldNotBeSameInstanceAs metadata
        result.toMap() shouldContainExactly
                mapOf(
                    "key1" to "value1",
                    "key2" to "value2"
                )
    }

    @Test
    fun `merge should combine with empty metadata`() {
        val metadata =
            Metadata.from(
                mapOf("key1" to "value1", "key2" to "value2")
            )

        val result = metadata.merge(Metadata())

        result shouldNotBeSameInstanceAs metadata
        result.toMap() shouldContainExactly
            mapOf(
                "key1" to "value1",
                "key2" to "value2"
            )
    }

    @Test
    fun `merge should throw an exception when there are common keys`() {
        val metadata1 =
            Metadata.from(
                mapOf("key1" to "value1", "key2" to "value2")
            )
        val metadata2 =
            Metadata.from(
                mapOf("key2" to "value3", "key3" to "value4")
            )

        val exception =
            assertThrows<IllegalArgumentException> {
                metadata1.merge(metadata2)
            }

       exception.message shouldBe "Metadata keys are not unique. Common keys: [key2]"
    }
}
