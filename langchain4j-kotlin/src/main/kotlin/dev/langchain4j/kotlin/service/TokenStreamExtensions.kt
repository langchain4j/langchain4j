package dev.langchain4j.kotlin.service

import dev.langchain4j.kotlin.model.chat.StreamingChatModelReply
import dev.langchain4j.service.TokenStream
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow

@JvmOverloads
public fun TokenStream.asFlow(bufferCapacity: Int = Channel.UNLIMITED): Flow<String> =
    flow {
        callbackFlow {
            onPartialResponse { trySend(it) }
            onCompleteResponse { close() }
            onError { close(it) }
            start()
            awaitClose()
        }.buffer(bufferCapacity).collect(this)
    }

@JvmOverloads
public fun TokenStream.asReplyFlow(bufferCapacity: Int = Channel.UNLIMITED): Flow<StreamingChatModelReply> =
    flow {
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
        }.buffer(bufferCapacity).collect(this)
    }
