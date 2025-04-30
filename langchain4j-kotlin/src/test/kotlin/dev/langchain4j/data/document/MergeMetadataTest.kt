package dev.langchain4j.data.document

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotSameInstanceAs
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

        assertThat(result.toMap()).isEqualTo(
            mapOf(
                "key1" to "value1",
                "key2" to "value2",
                "key3" to "value3",
                "key4" to "value4"
            )
        )
    }

    @Test
    fun `merge should combine with null object`() {
        val metadata =
            Metadata.from(
                mapOf("key1" to "value1", "key2" to "value2")
            )

        val result = metadata.merge(null)

        assertThat(result).isNotSameInstanceAs(metadata)
        assertThat(result.toMap()).isEqualTo(
            mapOf(
                "key1" to "value1",
                "key2" to "value2"
            )
        )
    }

    @Test
    fun `merge should combine with empty metadata`() {
        val metadata =
            Metadata.from(
                mapOf("key1" to "value1", "key2" to "value2")
            )

        val result = metadata.merge(Metadata())

        assertThat(result).isNotSameInstanceAs(metadata)
        assertThat(result.toMap()).isEqualTo(
            mapOf(
                "key1" to "value1",
                "key2" to "value2"
            )
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

        assertThat(exception.message)
            .isEqualTo("Metadata keys are not unique. Common keys: [key2]")
    }
}
