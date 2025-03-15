package dev.langchain4j.service;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotates one or more method parameters of an AI service interface to specify that the parameter
 * is an image content. This annotation can be used with {@link UserMessage} to specify that there
 * is an image content in the user message. It does not work when used in conjunction with {@link SystemMessage}.
 * <br>
 * An example:
 * <pre>
 * interface Assistant {
 *
 *     {@code @UserMessage}("Say hello to {{name}}")
 *     String greet(@V("name") String name, @ImageContent URI image);
 * }
 * </pre>
 * <p>
 * In this case, the {@code URI image} parameter is a URI pointing to the image content
 * <pre>
 * interface Assistant {
 *
 *     {@code @UserMessage}("You are a {{characteristic}} assistant")
 *     String chat(@UserMessage String userMessage, @ImageContent String base64Image);
 * }
 * </pre>
 * <p>
 * In this case, the {@code String base64Image} parameter is a base64 encoded image content.
 */
@Retention(RUNTIME)
@Target({METHOD, PARAMETER})
public @interface ImageContent {

    /**
     * The MIME type of the image content.
     */
    String mimeType() default "image/jpeg";
}
