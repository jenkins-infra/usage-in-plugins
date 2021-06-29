package org.jenkinsci.deprecatedusage;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Command line options for usages scan.
 * Not thread safe.
 */
public class Options {

    private static final String DEFAULT_UPDATE_CENTER_URL = "https://updates.jenkins-ci.org/update-center.json";
    private static final Options OPTIONS = new Options();
    private static List<String> additionalClasses;
    private static Map<String, Set<String>> additionalMethodNames;
    private static Map<String, Set<String>> additionalFields;

    @Option(name = "-h", aliases = "--help", usage = "Shows help")
    public boolean help;

    @Option(name = "-c", aliases = "--includeJavaCoreClasses", usage = "Include classes from java.* and javax.* in the report (not included by default)")
    public boolean includeJavaCoreClasses;

    @Option(name = "-C", aliases = "--additionalClasses", metaVar = "FILENAME", usage = "File name for additional classes to scan")
    public File additionalClassesFile;

    @Option(name = "-M", aliases = "--additionalMethods", metaVar = "FILENAME", usage = "File name for additional methods to scan")
    public File additionalMethodsFile;

    @Option(name = "-F", aliases = "--additionalFields", metaVar = "FILENAME", usage = "File name for additional fields to scan")
    public File additionalFieldsFile;

    @Option(name = "-i", aliases = "--onlyIncludeSpecified", usage = "Only include in the report the specified classes/methods/fields")
    public boolean onlyIncludeSpecified;

    @Option(name = "-p", aliases = "--includePluginLibs", usage = "Also scan libraries bundled inside plugins")
    public boolean includePluginLibraries;

    @Option(name = "--onlyIncludeJenkinsClasses", usage = "Only include in the report Jenkins related classes (jenkins.*, hudson.*, etc.")
    public boolean onlyIncludeJenkinsClasses;

    @Option(name = "-u", aliases = {"--updateCenter", "--updateCenters"}, usage = "Specifies update center URL(s) to fetch plugins from; use commas to separate multiple URLs")
    public String updateCenterURLs = DEFAULT_UPDATE_CENTER_URL;

    @Option(name = "-D", aliases = "--downloadConcurrent", metaVar = "COUNT", usage = "Specifies number of concurrent downloads to allow")
    public int maxConcurrentDownloads = Runtime.getRuntime().availableProcessors() * 4;

    @Option(name = "-r", aliases = "--recursive", usage = "Recursively check for method signatures (does not work for class/field at the moment)")
    public boolean recursive;
    
    @Option(name = "--recursiveMaxDepth", metaVar = "MAX_DEPTH", usage = "Maximum depth for the recursion, default to 5. Only considered if recursive mode is activated.")
    public int recursiveMaxDepth = 5;

    @Option(name = "-s", aliases = "--skipDownloads", usage = "Disable the download of the core/plugins and use the local version. Useful during development to debug more efficiently.")
    public boolean skipDownloads;

    @Option(name = "-v", aliases = "--verbose", usage = "Add verbose logging about downloads")
    public boolean verbose;

    private Options() {
    }

    public List<String> getUpdateCenterURLs() {
        String[] urls = StringUtils.split(updateCenterURLs, ',');
        if (urls == null) {
            return Collections.singletonList(DEFAULT_UPDATE_CENTER_URL);
        }
        return Arrays.stream(urls).map(String::trim).collect(Collectors.toList());
    }

    /**
     * Singleton
     */
    public static Options get() {
        return OPTIONS;
    }

    /**
     * Returns the additional classes if the related {@link #additionalClassesFile} has been specified.
     *
     * @throws IllegalArgumentException if called when {@link #additionalClassesFile} has not been specified.
     */
    public static List<String> getAdditionalClasses() throws IllegalArgumentException {
        if (additionalClasses != null) {
            return additionalClasses;
        }
        File additionalClassesFile = get().additionalClassesFile;
        if (!additionalClassesFile.exists()) {
            throw new IllegalArgumentException(
                    "Additional classes file option provided, but file cannot be found (" + additionalClassesFile + ")");
        }

        try {
            additionalClasses = new ArrayList<>();
            for (String line : Files.readAllLines(additionalClassesFile.toPath(), StandardCharsets.UTF_8)) {
                String trimmedLine = line.trim();
                if (!trimmedLine.startsWith("#")){
                    additionalClasses.add(trimmedLine.replaceAll("\\.", "/"));
                }
            }
            
            System.out.println(additionalClassesFile + " found, adding " + additionalClasses.size() + " classes");
            for (String additionalClass : additionalClasses) {
                System.out.println("\tadding " + additionalClass);
            }
            return additionalClasses;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Map<String, Set<String>> getAdditionalMethodNames() {
        if (additionalMethodNames != null) {
            return additionalMethodNames;
        }
        Path path = get().additionalMethodsFile.toPath();
        if (Files.notExists(path)) {
            throw new IllegalArgumentException("Additional methods file option provided, but file not found: " + path);
        }
        additionalMethodNames = new ConcurrentHashMap<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmedLine = line.trim();
                if (!trimmedLine.startsWith("#")){
                    int hashIndex = trimmedLine.indexOf('#');
                    if (hashIndex != -1) {
                        String className = trimmedLine.substring(0, hashIndex).replaceAll("\\.", "/");
                        String methodName = trimmedLine.substring(hashIndex + 1);
                        additionalMethodNames.computeIfAbsent(className, ignored -> new LinkedHashSet<>()).add(methodName);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return additionalMethodNames;
    }

    public static Map<String, Set<String>> getAdditionalFields() {
        if (additionalFields != null) {
            return additionalFields;
        }
        Path path = get().additionalFieldsFile.toPath();
        if (Files.notExists(path)) {
            throw new IllegalArgumentException("Additional fields file option provided, but file not found: " + path);
        }
        additionalFields = new ConcurrentHashMap<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmedLine = line.trim();
                if (!trimmedLine.startsWith("#")) {
                    int hashIndex = trimmedLine.indexOf('#');
                    if (hashIndex != -1) {
                        String className = trimmedLine.substring(0, hashIndex).replaceAll("\\.", "/");
                        String fieldName = trimmedLine.substring(hashIndex + 1);
                        additionalFields.computeIfAbsent(className, ignored -> new LinkedHashSet<>()).add(fieldName);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return additionalFields;
    }
}
