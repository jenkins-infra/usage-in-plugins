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

    protected void generateHtmlReport(Writer writer) throws IOException {
        SortedSet<DeprecatedUsage> set = new TreeSet<>(new Comparator<DeprecatedUsage>() {
            @Override
            public int compare(DeprecatedUsage o1, DeprecatedUsage o2) {
                return o1.getPlugin().compareTo(o2.getPlugin());
            }
        });
        set.addAll(usages);

        writer.append("<h1>Deprecated Usage By Plugin</h1>");

        for (DeprecatedUsage usage : set) {
            if (!usage.hasDeprecatedUsage()) {
                continue;
            }
            writer.append("<div class='plugin'><h2 id='" + usage.getPlugin().artifactId +"'><a href='" + usage.getPlugin().getUrl() + "'>" + usage.getPlugin().toString() + "</a></h2>");

            if (usage.getClasses().size() > 0) {
                writer.append("<h3>Classes</h3><ul>");
                for (String clazz : usage.getClasses().keySet()) {
                    if (!usage.getPlugin().artifactId.equals(usage.getClasses().get(clazz))) {
                        writer.append("<li>" + JavadocUtil.signatureToJenkinsdocLink(clazz) + " in " + usage.getClasses().get(clazz) + "</li>\n");
                    }
                }
                writer.append("</ul>\n\n");
            }

            if (usage.getMethods().size() > 0) {
                writer.append("<h3>Methods</h3><ul>");
                for (String method : usage.getMethods().keySet()) {
                    writer.append("<li>" + JavadocUtil.signatureToJenkinsdocLink(method) + " in " + usage.getMethods().get(method) + "</li>\n");
                }
                writer.append("</ul>\n\n");
            }

            if (usage.getFields().size() > 0) {
                writer.append("<h3>Fields</h3><ul>");
                for (String field : usage.getFields().keySet()) {
                    writer.append("<li>" + JavadocUtil.signatureToJenkinsdocLink(field) + " in " + usage.getFields().get(field) + "</li>\n");
                }
                writer.append("</ul>\n\n");
            }
            writer.append("</div>");
        }
    }

    protected void generateJsonReport(Writer writer) throws IOException {
        JSONObject map = new JSONObject();
        for (DeprecatedUsage usage : usages) {
            JSONObject plugin = new JSONObject();

            plugin.put("plugin", usage.getPlugin().toString());

            JSONArray classes = new JSONArray();
            for (String clazz : usage.getClasses().keySet()) {
                classes.put(clazz);
            }
            plugin.put("classes", classes);

            JSONArray methods = new JSONArray();
            for (String method : usage.getMethods().keySet()) {
                methods.put(method);
            }
            plugin.put("methods", methods);

            JSONArray fields = new JSONArray();
            for (String field : usage.getFields().keySet()) {
                fields.put(field);
            }
            plugin.put("fields", fields);

            map.put(usage.getPlugin().artifactId, plugin);
        }
        writer.append(map.toString(2));
    }
}
