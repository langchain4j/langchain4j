package dev.langchain4j.skills;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

import dev.langchain4j.Experimental;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.MalformedInputException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads skills from the classpath (including cases when they are packaged inside
 * a JAR file that is on the classpath).
 * <p>
 * Each skill must reside in its own directory containing a {@code SKILL.md} file.
 * The file must have a YAML front matter block that declares the skill's {@code name}
 * and {@code description}. The body of the file (below the front matter) becomes the
 * skill's {@link Skill#content() content}.
 * <p>
 * Any additional files in the skill directory are loaded as {@link SkillResource}s,
 * except {@code SKILL.md} itself and any files under a {@code scripts/} subdirectory.
 * Empty files are silently skipped.
 */
@Experimental
public class ClassPathSkillLoader {

    private static final Logger log = LoggerFactory.getLogger(ClassPathSkillLoader.class);

    private ClassPathSkillLoader() {}

    /**
     * Loads all skills found in immediate subdirectories of the given classpath directory.
     * Uses the thread's context class loader.
     *
     * @param directoryOnClasspath the classpath directory whose immediate subdirectories are scanned for skills
     * @return the list of loaded skills
     */
    public static List<Skill> loadSkills(String directoryOnClasspath) {
        return loadSkills(directoryOnClasspath, getDefaultClassLoader());
    }

    /**
     * Loads all skills found in immediate subdirectories of the given classpath directory.
     *
     * @param directoryOnClasspath the classpath directory whose immediate subdirectories are scanned for skills
     * @param classLoader          the class loader to use
     * @return the list of loaded skills
     */
    public static List<Skill> loadSkills(String directoryOnClasspath, ClassLoader classLoader) {
        ResolvedDirectory resolved = resolveClasspathDirectory(directoryOnClasspath, classLoader);

        try (Stream<Path> entries = Files.list(resolved.path)) {
            return entries.filter(Files::isDirectory)
                    .filter(dir -> Files.exists(dir.resolve("SKILL.md")))
                    .map((Path skillDirectory) ->
                            loadSkillFromPath(new ResolvedDirectory(skillDirectory, resolved.jarFileSystem, false)))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load skills from classpath directory: " + directoryOnClasspath, e);
        } finally {
            closeJarFileSystem(resolved);
        }
    }

    /**
     * Loads a single skill from the given classpath directory.
     * Uses the thread's context class loader.
     *
     * @param skillDirectoryOnClasspath the classpath path to the skill directory
     * @return the loaded skill
     */
    public static Skill loadSkill(String skillDirectoryOnClasspath) {
        return loadSkill(skillDirectoryOnClasspath, getDefaultClassLoader());
    }

    /**
     * Loads a single skill from the given classpath directory.
     *
     * @param skillDirectoryOnClasspath the classpath path to the skill directory
     * @param classLoader               the class loader to use
     * @return the loaded skill
     */
    public static Skill loadSkill(String skillDirectoryOnClasspath, ClassLoader classLoader) {
        ResolvedDirectory resolved = resolveClasspathDirectory(skillDirectoryOnClasspath, classLoader);
        try {
            return loadSkillFromPath(resolved);
        } finally {
            closeJarFileSystem(resolved);
        }
    }

    private static Skill loadSkillFromPath(ResolvedDirectory skillDirectory) {
        Path skillFile = skillDirectory.path.resolve("SKILL.md");

        if (!Files.exists(skillFile)) {
            throw new IllegalArgumentException("SKILL.md not found in " + skillDirectory);
        }

        try {
            String markdown = Files.readString(skillFile);

            Map<String, List<String>> frontMatter = SkillLoaderCommon.parseFrontMatter(markdown);
            String content = SkillLoaderCommon.extractContent(markdown);

            String name = SkillLoaderCommon.getSingle(frontMatter, "name");
            String description = SkillLoaderCommon.getSingle(frontMatter, "description");

            List<DefaultSkillResource> resources = loadResources(skillDirectory.path);

            if (skillDirectory.jarFileSystem != null) {
                // skill loaded from a JAR
                return DefaultSkill.builder()
                        .name(name)
                        .description(description)
                        .content(content)
                        .resources(resources)
                        .build();
            } else {
                // skill loaded from the regular filesystem
                return DefaultFileSystemSkill.builder()
                        .name(name)
                        .description(description)
                        .content(content)
                        .resources(resources)
                        .basePath(skillDirectory.path)
                        .build();
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to load skill from " + skillDirectory, e);
        }
    }

    private static List<DefaultSkillResource> loadResources(Path skillDirectory) {
        try (Stream<Path> files = Files.walk(skillDirectory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("SKILL.md"))
                    .filter(path -> {
                        String relativePath = stream(
                                        skillDirectory.relativize(path).spliterator(), false)
                                .map(Path::toString)
                                .collect(joining("/"));
                        return !relativePath.startsWith("scripts");
                    })
                    .map(path -> {
                        try {
                            String content = Files.readString(path);
                            if (isNullOrBlank(content)) {
                                return null;
                            }
                            String relativePath = stream(
                                            skillDirectory.relativize(path).spliterator(), false)
                                    .map(Path::toString)
                                    .collect(joining("/"));
                            return SkillResource.builder()
                                    .relativePath(relativePath)
                                    .content(content)
                                    .build();
                        } catch (MalformedInputException e) {
                            log.warn("Skipping binary file that cannot be read as UTF-8 text: {}", path);
                            return null;
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to load skill resource from " + path, e);
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load skill resources from " + skillDirectory, e);
        }
    }

    private record ResolvedDirectory(Path path, FileSystem jarFileSystem, boolean shouldCloseFileSystemAfterLoading) {}

    private static ResolvedDirectory resolveClasspathDirectory(String pathOnClasspath, ClassLoader classLoader) {
        URL url = classLoader.getResource(pathOnClasspath);
        if (url == null) {
            throw new IllegalArgumentException("Classpath resource not found: " + pathOnClasspath);
        }

        try {
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                return resolveJarPath(uri);
            }
            return new ResolvedDirectory(Path.of(uri), null, false);
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException("Failed to resolve classpath resource: " + pathOnClasspath, e);
        }
    }

    private static ResolvedDirectory resolveJarPath(URI jarUri) throws IOException {
        String schemeSpecific = jarUri.getSchemeSpecificPart();
        int separator = schemeSpecific.indexOf("!/");
        if (separator == -1) {
            throw new IllegalArgumentException("Invalid JAR URI: " + jarUri);
        }
        String pathInJar = schemeSpecific.substring(separator + 1);

        FileSystem fs;
        boolean created;
        try {
            fs = FileSystems.newFileSystem(jarUri, Map.of());
            created = true;
        } catch (FileSystemAlreadyExistsException e) {
            fs = FileSystems.getFileSystem(jarUri);
            created = false;
        }
        return new ResolvedDirectory(fs.getPath(pathInJar), fs, created);
    }

    private static void closeJarFileSystem(ResolvedDirectory resolvedDirectory) {
        if (resolvedDirectory.shouldCloseFileSystemAfterLoading() && resolvedDirectory.jarFileSystem() != null) {
            try {
                resolvedDirectory.jarFileSystem().close();
            } catch (IOException e) {
                log.warn("Failed to close JAR filesystem", e);
            }
        }
    }

    private static ClassLoader getDefaultClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }
}
