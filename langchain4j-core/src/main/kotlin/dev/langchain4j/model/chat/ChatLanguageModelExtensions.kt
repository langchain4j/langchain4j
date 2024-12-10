package dev.langchain4j.model.chat

import dev.langchain4j.Experimental
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.output.Response
import kotlinx.coroutines.coroutineScope

/**
 * Asynchronously processes a chat request using the language model within
 * a coroutine scope. This extension function provides a structured
 * concurrency wrapper around the synchronous [ChatLanguageModel.chat] method.
 *
 * Example usage:
 * ```kotlin
 * val response = model.chatAsync(ChatRequest(messages))
 * ```
 *
 * @param request The chat request containing messages and optional parameters
 *    for the model.
 * @return [ChatResponse] containing the model's response and any additional
 *    metadata.
 * @throws Exception if the chat request fails or is interrupted.
 * @see ChatLanguageModel.chat
 * @see ChatRequest
 * @see ChatResponse
 */
@Experimental
suspend fun ChatLanguageModel.chatAsync(request: ChatRequest): ChatResponse {
    val model = this
    return coroutineScope { model.chat(request) }
}

/**
 * Asynchronously processes a chat request using a [ChatRequest.Builder] for
 * convenient request configuration. This extension function combines the
 * builder pattern with coroutine-based asynchronous execution.
 *
 * Example usage:
 * ```kotlin
 * val response = model.chatAsync(ChatRequest.builder()
 *     .messages(listOf(UserMessage("Hello")))
 *     .temperature(0.7)
 *     .maxTokens(100))
 * ```
 *
 * @param requestBuilder The builder instance configured with desired chat
 *    request parameters.
 * @return [ChatResponse] containing the model's response and any additional
 *    metadata.
 * @throws Exception if the chat request fails, is interrupted, or the builder
 *    produces an invalid configuration.
 * @see ChatRequest
 * @see ChatResponse
 * @see ChatRequest.Builder
 * @see chatAsync
 */
@Experimental
suspend fun ChatLanguageModel.chatAsync(requestBuilder: ChatRequest.Builder): ChatResponse =
    chatAsync(requestBuilder.build())

/**
 * Processes a chat request using a [ChatRequest.Builder] for convenient request
 * configuration. This extension function provides a builder pattern alternative
 * to creating [ChatRequest] directly.
 *
 * Example usage:
 * ```kotlin
 * val response = model.chat(ChatRequest.builder()
 *     .messages(listOf(UserMessage("Hello")))
 *     .temperature(0.7)
 *     .maxTokens(100))
 * ```
 *
 * @param requestBuilder The builder instance configured with desired chat
 *    request parameters.
 * @return [ChatResponse] containing the model's response and any additional
 *    metadata.
 * @throws Exception if the chat request fails or the builder produces an
 *    invalid configuration.
 * @see ChatRequest
 * @see ChatResponse
 * @see ChatRequest.Builder
 */
@Experimental
fun ChatLanguageModel.chat(requestBuilder: ChatRequest.Builder): ChatResponse =
    this.chat(requestBuilder.build())

/**
 * Asynchronously generates a response for a list of chat messages using
 * the language model within a coroutine scope. This extension function
 * provides a structured concurrency wrapper around the synchronous
 * [ChatLanguageModel.generate] method.
 *
 * Example usage:
 * ```kotlin
 * val response = model.generateAsync(listOf(
 *     SystemMessage("You are a helpful assistant"),
 *     UserMessage("Hello!")
 * ))
 * ```
 *
 * @param messages The list of chat messages representing the conversation
 *    history and current prompt.
 * @return [Response] containing the AI's message and any additional metadata.
 * @throws Exception if the generation fails or is interrupted.
 * @see ChatLanguageModel.generate
 * @see ChatMessage
 * @see Response
 * @see AiMessage
 */
@Experimental
suspend fun ChatLanguageModel.generateAsync(messages: List<ChatMessage>): Response<AiMessage> {
    val model = this
    return coroutineScope { model.generate(messages) }
}
