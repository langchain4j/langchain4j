package dev.langchain4j.skills;

import dev.langchain4j.Experimental;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
 * <p>
 * When skills are loaded from a plain directory on the classpath (not from a JAR),
 * symbolic links inside a skill directory are followed only when they point to a file inside
 * the same skill directory. Symbolic links to directories inside a skill are not followed.
 * If {@code SKILL.md} or a resource is a symbolic link to a file outside of the skill directory,
 * loading fails with an {@link IllegalArgumentException}.
 * The skill directory itself may be a symbolic link; in that case, the files of the directory
 * it points to are loaded.
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
     * @throws IllegalArgumentException if the directory is not found on the classpath,
     *                                  or if a skill's {@code SKILL.md} or resource is a symbolic link to a file
     *                                  outside of its skill directory
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
     * @throws IllegalArgumentException if the directory is not found on the classpath,
     *                                  or if a skill's {@code SKILL.md} or resource is a symbolic link to a file
     *                                  outside of its skill directory
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
     * @throws IllegalArgumentException if the skill directory or its {@code SKILL.md} is not found,
     *                                  or if {@code SKILL.md} or a resource is a symbolic link to a file
     *                                  outside of the skill directory
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
     * @throws IllegalArgumentException if the skill directory or its {@code SKILL.md} is not found,
     *                                  or if {@code SKILL.md} or a resource is a symbolic link to a file
     *                                  outside of the skill directory
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
            Path realSkillDirectory = skillDirectory.path.toRealPath();
            String markdown = SkillLoaderCommon.readSkillMarkdown(realSkillDirectory);

            Map<String, List<String>> frontMatter = SkillLoaderCommon.parseFrontMatter(markdown);
            String content = SkillLoaderCommon.extractContent(markdown);

            String name = SkillLoaderCommon.getSingle(frontMatter, "name");
            String description = SkillLoaderCommon.getSingle(frontMatter, "description");

            List<DefaultSkillResource> resources = SkillLoaderCommon.loadResources(realSkillDirectory, log);

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
