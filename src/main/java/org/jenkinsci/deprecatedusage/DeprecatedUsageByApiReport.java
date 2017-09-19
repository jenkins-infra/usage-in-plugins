package org.jenkinsci.deprecatedusage;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * This report shows deprecated core and Stapler APIs that are actually used, grouped by API, listing plugins that use it.
 */
public class DeprecatedUsageByApiReport extends Report {

    private SortedMap<String, SortedSet<String>> deprecatedClassesToPlugins = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private SortedMap<String, SortedSet<String>> deprecatedFieldsToPlugins = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private SortedMap<String, SortedSet<String>> deprecatedMethodsToPlugins = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);


    public DeprecatedUsageByApiReport(DeprecatedApi api, List<DeprecatedUsage> usages, File outputDir, String reportName) {
        super(api, usages, outputDir, reportName);

        SortedMap<String, String> deprecatedClassesUsed = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        SortedMap<String, String> deprecatedFieldsUsed = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        SortedMap<String, String> deprecatedMethodsUsed = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        // collect all deprecated methods, classes and fields used across all plugins
        for (DeprecatedUsage usage : usages) {
            deprecatedClassesUsed.putAll(usage.getClasses());
            deprecatedFieldsUsed.putAll(usage.getFields());
            deprecatedMethodsUsed.putAll(usage.getMethods());
        }

        {
            for (String className : deprecatedClassesUsed.keySet()) {
                SortedSet<String> usingPlugins = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                for (DeprecatedUsage usage : usages) {
                    if (usage.getClasses().containsKey(className)) {
                        usingPlugins.add(usage.getPlugin().artifactId);
                    }
                }
                deprecatedClassesToPlugins.put(className, usingPlugins);
            }
        }

        {
            for (String fieldName : deprecatedFieldsUsed.keySet()) {
                SortedSet<String> usingPlugins = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                for (DeprecatedUsage usage : usages) {
                    if (usage.getFields().containsKey(fieldName)) {
                        usingPlugins.add(usage.getPlugin().artifactId);
                    }
                }
                deprecatedFieldsToPlugins.put(fieldName, usingPlugins);
            }
        }

        {
            for (String methodName : deprecatedMethodsUsed.keySet()) {
                SortedSet<String> usingPlugins = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                for (DeprecatedUsage usage : usages) {
                    if (usage.getMethods().containsKey(methodName)) {
                        usingPlugins.add(usage.getPlugin().artifactId);
                    }
                }
                deprecatedMethodsToPlugins.put(methodName, usingPlugins);
            }
        }
    }

    protected void generateHtmlReport(Writer writer) throws IOException {
        writer.append("<h1>Deprecated Usage in Plugins By API</h1>");

        {
            writer.append("<h2>Classes</h2>\n");
            for (Map.Entry<String, SortedSet<String>> entry : deprecatedClassesToPlugins.entrySet()) {
                writer.append("<div class='class'>\n");
                writer.append("<h3 id='" + entry.getKey().replaceAll("[^a-zA-Z0-9-]", "_") + "'>" + JavadocUtil.signatureToJenkinsdocLink(entry.getKey()) + "</h3><ul>\n");
                for (String plugin : entry.getValue()) {
                    writer.append("<li><a href='http://plugins.jenkins.io/" + plugin + "'>" + plugin + "</a></li>\n");
                }
                writer.append("</ul></div>\n\n");
            }
        }

        {
            writer.append("<h2>Fields</h2>\n");
            for (Map.Entry<String, SortedSet<String>> entry : deprecatedFieldsToPlugins.entrySet()) {
                writer.append("<div class='field'>\n");
                writer.append("<h3 id='" + entry.getKey().replaceAll("[^a-zA-Z0-9-]", "_") + "'>" + JavadocUtil.signatureToJenkinsdocLink(entry.getKey()) + "</h3><ul>\n");
                for (String plugin : entry.getValue()) {
                    writer.append("<li><a href='http://plugins.jenkins.io/" + plugin + "'>" + plugin + "</a></li>\n");
                }
                writer.append("</ul></div>\n\n");
            }
        }

        {
            writer.append("<h2>Methods</h2>\n");
            for (Map.Entry<String, SortedSet<String>> entry : deprecatedMethodsToPlugins.entrySet()) {
                writer.append("<div class='method'>\n");
                writer.append("<h3 id='" + entry.getKey().replaceAll("[^a-zA-Z0-9-]", "_") + "'>" + JavadocUtil.signatureToJenkinsdocLink(entry.getKey()) + "</h3><ul>\n");
                for (String plugin : entry.getValue()) {
                    writer.append("<li><a href='http://plugins.jenkins.io/" + plugin + "'>" + plugin + "</a></li>\n");
                }
                writer.append("</ul></div>\n\n");
            }
        }
    }

    protected void generateJsonReport(Writer writer) throws IOException {
        JSONObject map = new JSONObject();

        map.put("classes", deprecatedClassesToPlugins);
        map.put("methods", deprecatedMethodsToPlugins);
        map.put("fields", deprecatedFieldsToPlugins);

        writer.append(map.toString(2));
    }
}
