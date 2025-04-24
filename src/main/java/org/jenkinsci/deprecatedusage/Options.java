package org.jenkinsci.deprecatedusage;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Command line options for usages scan.
 * Not thread safe.
 */
public class Options {

    // set via Main
    private static String DEFAULT_UPDATE_CENTER_URL = "https://updates.jenkins-ci.org/update-center.json";
    private static Options OPTIONS = new Options();
    private static Set<String> additionalClasses;
    private static Map<String, Set<String>> additionalMethodNames;
    private static Map<String, Set<String>> additionalFields;
    private static Set<String> limitedScopeOfPlugins;

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

    @Option(name = "-l", aliases = "--limitPlugins", metaVar = "FILENAME", usage = "File name for the limitation of the scope of plugins to scan")
    public File limitPluginsFile;

    @Option(name = "-i", aliases = "--onlyIncludeSpecified", usage = "Only include in the report the specified classes/methods/fields")
    public boolean onlyIncludeSpecified;

    @Option(name = "-p", aliases = "--includePluginLibs", usage = "Also scan libraries bundled inside plugins")
    public boolean includePluginLibraries;

    @Option(name = "-P", aliases = "--onlyPlugins", usage = "Only scan plugins with the specified plugin IDs (comma separated)")
    public String plugins;

    @Option(name = "--includeCore", usage = "Scan the Cores in addition to the plugins")
    public boolean includeCore;

    @Option(name = "--includeCoreLibs", usage = "Also scan libraries bundled inside cores")
    public boolean includeCoreLibraries;

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

    public boolean shouldScanPlugin(String pluginId) {
        if (plugins == null) {
            return true;
        }
        return Arrays.stream(plugins.split(",")).map(String::trim).anyMatch(it -> it.equals(pluginId));
    }

    /**
     * Singleton
     */
    public static Options get() {
        return OPTIONS;
    }

    public void buildCache() {
        if (additionalClassesFile != null) {
            buildAdditionalClasses();
        }
        if (additionalMethodsFile != null) {
            buildAdditionalMethodNames();
        }
        if (additionalFieldsFile != null) {
            buildAdditionalFields();
        }
        if (limitPluginsFile != null) {
            buildLimitedScopeOfPlugins();
        }
    }

    /**
     * Returns the additional classes if the related {@link #additionalClassesFile} has been specified.
     *
     * @throws IllegalArgumentException if called when {@link #additionalClassesFile} has not been specified.
     */
    public static Set<String> getAdditionalClasses() {
        if (additionalClasses == null) {
            throw new IllegalArgumentException("Additional classes file option not provided, use '-C' or '--additionalClasses'");
        }
        return additionalClasses;
    }

    private void buildAdditionalClasses() {
        Path path = additionalClassesFile.toPath();
        if (Files.notExists(path)) {
            throw new IllegalArgumentException("Additional classes file option provided, but file not found: " + path);
        }

        additionalClasses = new HashSet<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmedLine = line.trim();
                if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#")) {
                    String className = trimmedLine.replaceAll("\\.", "/");
                    additionalClasses.add(className);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        System.out.println("Additional classes: " + additionalClassesFile + " found, adding " + additionalClasses.size() + " classes");
        for (String additionalClass : additionalClasses) {
            System.out.println("\tadding " + additionalClass);
        }
    }

    /**
     * Returns the additional methods  if the related {@link #additionalMethodsFile} has been specified.
     *
     * @throws IllegalArgumentException if called when {@link #additionalMethodsFile} has not been specified.
     */
    public static Map<String, Set<String>> getAdditionalMethodNames() {
        if (additionalMethodNames == null) {
            throw new IllegalArgumentException("Additional methods file option not provided, use '-M' or '--additionalMethods'");
        }
        return additionalMethodNames;
    }

    private void buildAdditionalMethodNames() {
        Path path = additionalMethodsFile.toPath();
        if (Files.notExists(path)) {
            throw new IllegalArgumentException("Additional methods file option provided, but file not found: " + path);
        }
        additionalMethodNames = new HashMap<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmedLine = line.trim();
                if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#")) {
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

        System.out.println("Additional methods: " + additionalMethodNames.values().stream().mapToInt(Set::size).sum());
    }


    /**
     * Returns the additional fields if the related {@link #additionalFieldsFile} has been specified.
     *
     * @throws IllegalArgumentException if called when {@link #additionalFieldsFile} has not been specified.
     */
    public static Map<String, Set<String>> getAdditionalFields() {
        if (additionalFields == null) {
            throw new IllegalArgumentException("Additional fields file option not provided, use '-F' or '--additionalFields'");
        }
        return additionalFields;
    }

    private void buildAdditionalFields() {
        Path path = additionalFieldsFile.toPath();
        if (Files.notExists(path)) {
            throw new IllegalArgumentException("Additional fields file option provided, but file not found: " + path);
        }

        additionalFields = new HashMap<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmedLine = line.trim();
                if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#")) {
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

        System.out.println("Additional fields: " + additionalFields.values().stream().mapToInt(Set::size).sum());
    }

    /**
     * Returns the limited scope of plugins if the related {@link #limitPluginsFile} has been specified.
     *
     * @throws IllegalArgumentException if called when {@link #limitPluginsFile} has not been specified.
     */
    public static Set<String> getLimitedScopeOfPlugins() {
        if (limitedScopeOfPlugins == null) {
            throw new IllegalArgumentException("Limited scope of plugin file option not provided, use '-l' or '--limitPlugins'");
        }
        return limitedScopeOfPlugins;
    }

    private void buildLimitedScopeOfPlugins() {
        Path path = limitPluginsFile.toPath();
        if (Files.notExists(path)) {
            throw new IllegalArgumentException("Limited scope of plugin file option provided, but file not found: " + path);
        }

        limitedScopeOfPlugins = new HashSet<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmedLine = line.trim();
                if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#")) {
                    limitedScopeOfPlugins.add(trimmedLine);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        System.out.println("Limited scope of plugins: " + limitedScopeOfPlugins.size());
    }
}
