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

        SortedSet<String> deprecatedClassesUsed = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        SortedSet<String> deprecatedFieldsUsed = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        SortedSet<String> deprecatedMethodsUsed = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        {
            for (String className : api.getClasses()) {
                SortedSet<String> usingPluginsClass = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                SortedSet<String> usingPluginsMethod = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                SortedSet<String> usingPluginsField = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                for (DeprecatedUsage usage : usages) {
                    if (usage.getAcegiToClasses().containsKey(className)) {
                        usingPluginsClass.add(usage.getPlugin().artifactId);
                    }
                    if (usage.getAcegiToMethods().containsKey(className)) {
                        usingPluginsMethod.add(usage.getPlugin().artifactId);
                    }
                    if (usage.getAcegiToFields().containsKey(className)) {
                        usingPluginsField.add(usage.getPlugin().artifactId);
                    }
                }
                deprecatedClassesToPlugins.put(className, usingPluginsClass);
                deprecatedMethodsToPlugins.put(className, usingPluginsMethod);
                deprecatedFieldsToPlugins.put(className, usingPluginsField);
            }
        }
        //        
        //        
        //        // collect all deprecated methods, classes and fields used across all plugins
        //        for (DeprecatedUsage usage : usages) {
        //            deprecatedClassesUsed.addAll(usage.getClasses());
        //            deprecatedFieldsUsed.addAll(usage.getFields());
        //            deprecatedMethodsUsed.addAll(usage.getMethods());
        //        }
        //
        //        {
        //            for (String className : deprecatedClassesUsed) {
        //                SortedSet<String> usingPlugins = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        //                for (DeprecatedUsage usage : usages) {
        //                    if (usage.getClasses().contains(className)) {
        //                        usingPlugins.add(usage.getPlugin().artifactId);
        //                    }
        //                }
        //                deprecatedClassesToPlugins.put(className, usingPlugins);
        //            }
        //        }
        //
        //        {
        //            for (String fieldName : deprecatedFieldsUsed) {
        //                SortedSet<String> usingPlugins = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        //                for (DeprecatedUsage usage : usages) {
        //                    if (usage.getFields().contains(fieldName)) {
        //                        usingPlugins.add(usage.getPlugin().artifactId);
        //                    }
        //                }
        //                deprecatedFieldsToPlugins.put(fieldName, usingPlugins);
        //            }
        //        }
        //
        //        {
        //            for (String methodName : deprecatedMethodsUsed) {
        //                SortedSet<String> usingPlugins = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        //                for (DeprecatedUsage usage : usages) {
        //                    if (usage.getMethods().contains(methodName)) {
        //                        usingPlugins.add(usage.getPlugin().artifactId);
        //                    }
        //                }
        //                deprecatedMethodsToPlugins.put(methodName, usingPlugins);
        //            }
        //        }
    }

    protected void generateHtmlReport(Writer writer) throws IOException {
        writer.append("<h1>Deprecated Usage in Plugins By API</h1>");

        {
            writer.append("<h2>Classes (as SuperType, Parameter, ReturnType, Exception)</h2>\n");

            Map<String, SortedSet<String>> acegiToPlugin = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            deprecatedClassesToPlugins.forEach((acegiClass, plugins) ->
                    acegiToPlugin.computeIfAbsent(acegiClass, s -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER))
                            .addAll(plugins)
            );
            deprecatedMethodsToPlugins.forEach((acegiClass, plugins) ->
                    acegiToPlugin.computeIfAbsent(acegiClass, s -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER))
                            .addAll(plugins)
            );
            deprecatedFieldsToPlugins.forEach((acegiClass, plugins) ->
                    acegiToPlugin.computeIfAbsent(acegiClass, s -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER))
                            .addAll(plugins)
            );

            for (Map.Entry<String, SortedSet<String>> entry : acegiToPlugin.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    writer.append("<div class='class'>\n");
                    writer.append("<h3 id='" + entry.getKey().replaceAll("[^a-zA-Z0-9-]", "_") + "'>" + JavadocUtil.signatureToJenkinsdocLink(entry.getKey()) + "</h3><ul>\n");
                    for (String plugin : entry.getValue()) {
                        writer.append("<li><a href='http://plugins.jenkins.io/" + plugin + "'>" + plugin + "</a></li>\n");
                    }
                    writer.append("</ul></div>\n\n");
                }
            }
        }

//        {
        //            writer.append("<h2>Classes</h2>\n");
        //            for (Map.Entry<String, SortedSet<String>> entry : deprecatedClassesToPlugins.entrySet()) {
        //                if (!entry.getValue().isEmpty()) {
        //                    writer.append("<div class='class'>\n");
        //                    writer.append("<h3 id='" + entry.getKey().replaceAll("[^a-zA-Z0-9-]", "_") + "'>" + JavadocUtil.signatureToJenkinsdocLink(entry.getKey()) + "</h3><ul>\n");
        //                    for (String plugin : entry.getValue()) {
        //                        writer.append("<li><a href='http://plugins.jenkins.io/" + plugin + "'>" + plugin + "</a></li>\n");
        //                    }
        //                    writer.append("</ul></div>\n\n");
        //                }
        //            }
        //        }
        //
        //        {
        //            writer.append("<h2>Fields</h2>\n");
        //            for (Map.Entry<String, SortedSet<String>> entry : deprecatedFieldsToPlugins.entrySet()) {
        //                if (!entry.getValue().isEmpty()) {
        //                    writer.append("<div class='field'>\n");
        //                    writer.append("<h3 id='" + entry.getKey().replaceAll("[^a-zA-Z0-9-]", "_") + "'>" + JavadocUtil.signatureToJenkinsdocLink(entry.getKey()) + "</h3><ul>\n");
        //                    for (String plugin : entry.getValue()) {
        //                        writer.append("<li><a href='http://plugins.jenkins.io/" + plugin + "'>" + plugin + "</a></li>\n");
        //                    }
        //                    writer.append("</ul></div>\n\n");
        //                }
        //            }
        //        }
        //
        //        {
        //            writer.append("<h2>Methods</h2>\n");
        //            for (Map.Entry<String, SortedSet<String>> entry : deprecatedMethodsToPlugins.entrySet()) {
        //                if (!entry.getValue().isEmpty()) {
        //                    writer.append("<div class='method'>\n");
        //                    writer.append("<h3 id='" + entry.getKey().replaceAll("[^a-zA-Z0-9-]", "_") + "'>" + JavadocUtil.signatureToJenkinsdocLink(entry.getKey()) + "</h3><ul>\n");
        //                    for (String plugin : entry.getValue()) {
        //                        writer.append("<li><a href='http://plugins.jenkins.io/" + plugin + "'>" + plugin + "</a></li>\n");
        //                    }
        //                    writer.append("</ul></div>\n\n");
        //                }
        //            }
        //        }
    }

    protected void generateJsonReport(Writer writer) throws IOException {
        JSONObject map = new JSONObject();

        map.put("classes", deprecatedClassesToPlugins);
        map.put("methods", deprecatedMethodsToPlugins);
        map.put("fields", deprecatedFieldsToPlugins);

        writer.append(map.toString(2));
    }
}
