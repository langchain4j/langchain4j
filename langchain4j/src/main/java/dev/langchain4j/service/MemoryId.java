package dev.langchain4j.service;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * The value of a method parameter annotated with @MemoryId will be used to find the memory belonging to that user/conversation.
 * <p>
 * A parameter annotated with @MemoryId can be of any type, provided it has properly implemented equals() and hashCode() methods.
 * <p>
 * When {@link MemoryId} is used, the {@link ChatMemoryProvider} should be configured for the AI service.
 */
@Retention(RUNTIME)
@Target(PARAMETER)
public @interface MemoryId {}
