package dev.langchain4j.service;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Specifies a message with a content. @see {@link dev.langchain4j.data.message.Content}
 * {@code @ContentMessage} can be used with method parameters:
 * <pre>
 * interface Assistant {
 *
 *     {@code @SystemMessage}("You are a {{characteristic}} assistant")
 *     String chat(@UserMessage String userMessage, @V("characteristic") String characteristic, @ContentMessage List<ImageContent> images, @ContentMessage PdfFileContent file);
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({PARAMETER})
public @interface ContentMessage {

}
