package dev.langchain4j.kotlin.service

import dev.langchain4j.kotlin.model.chat.StreamingChatModelReply
import dev.langchain4j.service.TokenStream
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/**
 * Defines the default buffer capacity used for buffering operations or TokenStream processing.
 *
 * This constant is set to ensure consistent and optimal memory management when handling
 * buffers, providing a balance between performance and resource usage.
 * Depending on the context, it can be used as a standard size to initialize or manage buffers in memory.
 */
public const val DEFAULT_BUFFER_CAPACITY: Int = 32768

/**
 *
 * Converts a [TokenStream] into a [Flow] of strings which emits partial responses
 * as they are streamed, and closes when the stream is complete or encounters an error.
 *
 * @param bufferCapacity The capacity of the buffer used in the flow to control backpressure.
 *    Defaults to [DEFAULT_BUFFER_CAPACITY] if not specified.
 *    Use value [kotlinx.coroutines.channels.Channel.UNLIMITED]
 *    if you are feeling optimistic about [java.lang.OutOfMemoryError]s.
 * @return A [Flow] emitting strings, where each string represents a partial response
 *    from the associated language model.
 */
@JvmOverloads
public fun TokenStream.asFlow(
    bufferCapacity: Int = DEFAULT_BUFFER_CAPACITY,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    includeCompleteResponse: Boolean = false
): Flow<String> =
    callbackFlow {
        onPartialResponse { trySend(it) }
        onCompleteResponse {
            it.aiMessage()?.text()?.let { text ->
                if (includeCompleteResponse) {
                    trySend(text)
                }
            }
            close()
        }
        onError { close(it) }
        start()
        awaitClose()
    }.buffer(
        capacity = bufferCapacity, onBufferOverflow = onBufferOverflow
    )

/**
 * Converts a `TokenStream` into a `Flow` of `StreamingChatModelReply` instances, where each
 * emitted item represents a partial or complete response received during streaming.
 *
 * This function utilizes a coroutine-based flow to provide real-time updates of the
 * streaming response. The flow handles partial responses, the final complete response, and
 * errors that may occur during the streaming process. Responses are buffered with the specified
 * capacity.
 *
 * @param bufferCapacity The capacity of the flow buffer, which determines how many items can
 *                       be collected before backpressure occurs. Defaults to [DEFAULT_BUFFER_CAPACITY].
 *                       Use value [kotlinx.coroutines.channels.Channel.UNLIMITED]
 *                       if you are feeling optimistic about [java.lang.OutOfMemoryError].
 * @return A `Flow` that will emit `StreamingChatModelReply` instances including partial
 *         responses, complete responses, or errors in the order they are received.
 */
@JvmOverloads
public fun TokenStream.asReplyFlow(
    bufferCapacity: Int = DEFAULT_BUFFER_CAPACITY,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND
): Flow<StreamingChatModelReply> =
    callbackFlow {
        onPartialResponse { token ->
            trySend(StreamingChatModelReply.PartialResponse(token))
        }
        onCompleteResponse { response ->
            trySend(StreamingChatModelReply.CompleteResponse(response))
            close()
        }
        onError { throwable -> close(throwable) }
        start()
        awaitClose()
    }.buffer(capacity = bufferCapacity, onBufferOverflow = onBufferOverflow)
