/**
 * Downloading media (images, audio, video and PDF files) from URLs.
 * <p>
 * Some model providers accept media only as content, not as a URL. Their LangChain4j integrations download the media
 * of a message with a {@link MediaFetcher}. By default this is a {@link DefaultMediaFetcher}, which downloads only
 * {@code http} and {@code https} URLs of public hosts, never local files, with a timeout and a size limit. The
 * downloaded media is a {@link FetchedMedia}.
 *
 * @since 1.25.0
 */
@Experimental
package dev.langchain4j.data.media;

import dev.langchain4j.Experimental;
