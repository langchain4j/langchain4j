package dev.langchain4j.data.document.source;

import static dev.langchain4j.data.document.Document.FILE_NAME;
import static dev.langchain4j.data.document.Document.URL;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Specialization of a {@link DocumentSource} that knows how to read from the classpath.
 * <p>
 *   Use {@link dev.langchain4j.data.document.loader.ClassPathDocumentLoader} to load
 *   {@link dev.langchain4j.data.document.Document Document}s using this.
 * </p>
 */
public class ClassPathSource implements DocumentSource {
    private final URL url;
    private final ClassLoader classLoader;
    private final Metadata metadata = new Metadata();

    protected ClassPathSource(URL url, ClassLoader classLoader) {
        this.url = ensureNotNull(url, "url");
        this.classLoader = ensureNotNull(classLoader, "classLoader");

        var file = this.url.getFile();
        this.metadata.put(URL, file);
        this.metadata.put(FILE_NAME, file.substring(file.lastIndexOf('/') + 1));
    }

    /**
     * Creates a new instance of {@link ClassPathSource} from the given classpath resource string,
     * using {@code Thread.currentThread().getContextClassLoader()} as the class loader.
     *
     * @param classPathResource The path of the classpath resource to be loaded.
     * @return A {@link ClassPathSource} instance representing the classpath resource.
     * @throws IllegalArgumentException if the classpath resource is blank or cannot be found.
     * @see #from(String, ClassLoader)
     */
    public static ClassPathSource from(String classPathResource) {
        return from(classPathResource, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Creates a new instance of {@link ClassPathSource} from the given classpath resource and classloader.
     *
     * @param classPathResource The path of the classpath resource to be loaded. Must not be blank.
     * @param classLoader The class loader to use for loading the resource. If {@code null} then uses {@code Thread.currentThread().getContextClassLoader()} as the class loader.
     * @return A {@link ClassPathSource} instance representing the classpath resource.
     * @throws IllegalArgumentException If the classpath resource is blank or cannot be found, or if the class loader is null.
     */
    public static ClassPathSource from(String classPathResource, ClassLoader classLoader) {
        var resource = ensureNotBlank(classPathResource, "classPathResource");
        var cl = (classLoader != null) ? classLoader : Thread.currentThread().getContextClassLoader();
        var url = ensureNotNull(
                cl.getResource(resource), "'%s' was not found as a classpath resource", classPathResource);

        return new ClassPathSource(url, classLoader);
    }

    /**
     * Retrieves the URL associated with this {@link ClassPathSource}.
     * @return The {@link URL} instance representing the classpath resource.
     */
    public URL url() {
        return this.url;
    }

    /**
     * The {@link ClassLoader} that was used to load this {@link  ClassPathSource}
     * @return The {@link ClassLoader} that was used to load this {@link  ClassPathSource}
     */
    public ClassLoader classLoader() {
        return this.classLoader;
    }

    /**
     * Determines if the resource represented by this {@link ClassPathSource} is inside an archive.
     * @return {@code true} if the resource is packaged inside an archive, otherwise {@code false}.
     */
    public boolean isInsideArchive() {
        return "jar".equalsIgnoreCase(this.url.getProtocol());
    }

    @Override
    public InputStream inputStream() throws IOException {
        return this.url.openStream();
    }

    @Override
    public Metadata metadata() {
        return this.metadata;
    }
}
