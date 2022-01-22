package org.jenkinsci.deprecatedusage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * This report shows deprecated APIs in Jenkins and Stapler that are used by plugins, grouped by the plugins, listing APIs.
 */
public class DeprecatedUsageByPluginReport extends Report {
    public DeprecatedUsageByPluginReport(DeprecatedApi api, List<DeprecatedUsage> usages, File outputDir, String reportName) {
        super(api, usages, outputDir, reportName);
    }

    @Override
    protected void generateHtmlReport(Writer writer) throws IOException {
        SortedSet<DeprecatedUsage> set = new TreeSet<>(Comparator.comparing(DeprecatedUsage::getPlugin));
        set.addAll(usages);

        writer.append("<h1>Deprecated Usage By Plugin</h1>");

        for (DeprecatedUsage usage : set) {
            if (!usage.hasDeprecatedUsage()) {
                continue;
            }
            writer.append("<div class='plugin'><h2 id='").append(usage.getPlugin().artifactId).append("'><a href='")
                    .append(usage.getPlugin().getUrl()).append("'>").append(usage.getPlugin().toString()).append("</a></h2>");

            if (usage.getClasses().size() > 0) {
                writer.append("<h3>Classes</h3><ul>");
                for (String clazz : usage.getClasses()) {
                    writer.append("<li>").append(JavadocUtil.signatureToJenkinsdocLink(clazz)).append("</li>\n");
                }
                writer.append("</ul>\n\n");
            }

            if (usage.getMethods().size() > 0) {
                writer.append("<h3>Methods</h3><ul>");
                for (String method : usage.getMethods()) {
                    writer.append("<li>").append(JavadocUtil.signatureToJenkinsdocLink(method)).append("</li>\n");
                }
                writer.append("</ul>\n\n");
            }

            if (usage.getFields().size() > 0) {
                writer.append("<h3>Fields</h3><ul>");
                for (String field : usage.getFields()) {
                    writer.append("<li>").append(JavadocUtil.signatureToJenkinsdocLink(field)).append("</li>\n");
                }
                writer.append("</ul>\n\n");
            }
            writer.append("</div>");
        }
    }

    @Override
    protected void generateJsonReport(Writer writer) throws IOException {
        JSONObject map = new JSONObject();
        for (DeprecatedUsage usage : usages) {
            JSONObject plugin = new JSONObject();

            plugin.put("plugin", usage.getPlugin().toString());

            JSONArray classes = new JSONArray();
            for (String clazz : usage.getClasses()) {
                classes.put(clazz);
            }
            plugin.put("classes", classes);

            JSONArray methods = new JSONArray();
            for (String method : usage.getMethods()) {
                methods.put(method);
            }
            plugin.put("methods", methods);

            JSONArray fields = new JSONArray();
            for (String field : usage.getFields()) {
                fields.put(field);
            }
            plugin.put("fields", fields);

            map.put(usage.getPlugin().artifactId, plugin);
        }
        writer.append(map.toString(2));
    }
}
