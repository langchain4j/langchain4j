package dev.langchain4j.data.media;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Media (an image, audio, video or PDF file) downloaded by a {@link MediaFetcher}: its content and, if known, its MIME
 * type.
 * <p>
 * A custom {@link MediaFetcher} creates it with {@link #from(byte[], String)}, or with {@link #from(byte[])} when the
 * MIME type is not known:
 * <pre>{@code
 * FetchedMedia media = FetchedMedia.from(bytes, "image/png");
 * }</pre>
 *
 * @since 1.25.0
 */
@Experimental
public final class FetchedMedia {

    private final byte[] bytes;
    private final @Nullable String mimeType;

    private FetchedMedia(byte[] bytes, @Nullable String mimeType) {
        this.bytes = ensureNotNull(bytes, "bytes");
        this.mimeType = isNullOrBlank(mimeType) ? null : mimeType;
    }

    /**
     * Creates downloaded media.
     *
     * @param bytes    the content. The array is not copied, so do not change it afterwards.
     * @param mimeType the MIME type, for example {@code "image/png"}, or {@code null} (or blank) if it is not known.
     * @return the downloaded media.
     */
    public static FetchedMedia from(byte[] bytes, @Nullable String mimeType) {
        return new FetchedMedia(bytes, mimeType);
    }

    /**
     * Creates downloaded media whose MIME type is not known.
     *
     * @param bytes the content. The array is not copied, so do not change it afterwards.
     * @return the downloaded media.
     */
    public static FetchedMedia from(byte[] bytes) {
        return new FetchedMedia(bytes, null);
    }

    /**
     * Returns the content. The array is not copied (media can be large), so do not change it.
     *
     * @return the content.
     */
    public byte[] bytes() {
        return bytes;
    }

    /**
     * Returns the MIME type of the content, for example {@code "image/png"}. For media downloaded by
     * {@link DefaultMediaFetcher}, it is the {@code Content-Type} header of the response without its parameters
     * (such as {@code charset}), in lower case.
     *
     * @return the MIME type, or {@code null} if it is not known.
     */
    public @Nullable String mimeType() {
        return mimeType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FetchedMedia that = (FetchedMedia) o;
        return Arrays.equals(this.bytes, that.bytes) && Objects.equals(this.mimeType, that.mimeType);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(bytes) + Objects.hashCode(mimeType);
    }

    @Override
    public String toString() {
        return "FetchedMedia { sizeInBytes = " + bytes.length + ", mimeType = " + quoted(mimeType) + " }";
    }
}
