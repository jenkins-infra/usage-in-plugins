package org.jenkinsci.deprecatedusage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
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
            writer.append("<div class='plugin'><h2 id='" + usage.getPlugin().artifactId + "'><a href='" + usage.getPlugin().getUrl() + "'>" + usage.getPlugin().toString() + "</a></h2>");

            Set<String> acegiClasses = new TreeSet<>();
            acegiClasses.addAll(usage.getAcegiToClasses().keySet());
            acegiClasses.addAll(usage.getAcegiToMethods().keySet());
            acegiClasses.addAll(usage.getAcegiToFields().keySet());

            for (String acegiClass : acegiClasses) {
                writer.append("<h3>Acegi class: " + acegiClass + "</h3>");
                List<String> cs = usage.getAcegiToClasses().get(acegiClass);
                if (cs != null) {
                    writer.append("<h4>Classes</h4><ul>");
                    for (String clazz : cs) {
                        writer.append("<li>" + JavadocUtil.signatureToJenkinsdocLink(clazz) + "</li>\n");
                    }
                    writer.append("</ul>\n\n");
                }

                List<String> ms = usage.getAcegiToMethods().get(acegiClass);
                if (ms != null) {
                    writer.append("<h4>Methods</h4><ul>");
                    for (String method : ms) {
                        writer.append("<li>" + JavadocUtil.signatureToJenkinsdocLink(method) + "</li>\n");
                    }
                    writer.append("</ul>\n\n");
                }

                List<String> fs = usage.getAcegiToFields().get(acegiClass);
                if (fs != null) {
                    writer.append("<h4>Fields</h4><ul>");
                    for (String field : fs) {
                        writer.append("<li>" + JavadocUtil.signatureToJenkinsdocLink(field) + "</li>\n");
                    }
                    writer.append("</ul>\n\n");
                }
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
