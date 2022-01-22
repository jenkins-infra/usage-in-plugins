package org.jenkinsci.deprecatedusage;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class DeprecatedUnusedApiReport extends Report {
    public DeprecatedUnusedApiReport(DeprecatedApi api, List<DeprecatedUsage> usages, File outputDir, String reportName) {
        super(api, usages, outputDir, reportName);
    }

    @Override
    protected void generateHtmlReport(Writer writer) throws IOException {
        writer.append("<h1>Unused Deprecated APIs</h1>");

        {
            writer.append("<div class='classes'><h2>Classes</h2><ul>\n");
            CLASSES:
            for (String deprecatedClass : new TreeSet<>(api.getClasses())) {
                if (!isRelevantSignature(deprecatedClass)) {
                    continue;
                }
                for (DeprecatedUsage usage : usages) {
                    if (usage.getClasses().contains(deprecatedClass)) {
                        continue CLASSES;
                    }
                }
                writer.append("<li>").append(JavadocUtil.signatureToJenkinsdocLink(deprecatedClass)).append("</li>\n");
            }
            writer.append("</div>");
        }

        {
            writer.append("<div class='fields'><h2>Fields</h2><ul>\n");
            FIELDS:
            for (String deprecatedField : new TreeSet<>(api.getFields())) {
                if (!isRelevantSignature(deprecatedField)) {
                    continue;
                }
                for (DeprecatedUsage usage : usages) {
                    if (usage.getFields().contains(deprecatedField)) {
                        continue FIELDS;
                    }
                }
                writer.append("<li>").append(JavadocUtil.signatureToJenkinsdocLink(deprecatedField)).append("</li>\n");
            }
            writer.append("</div>");
        }

        {
            writer.append("<div class='methods'><h2>Methods</h2><ul>\n");
            METHODS:
            for (String deprecatedMethod : new TreeSet<>(api.getMethods())) {
                if (!isRelevantSignature(deprecatedMethod)) {
                    continue;
                }
                for (DeprecatedUsage usage : usages) {
                    if (usage.getMethods().contains(deprecatedMethod)) {
                        continue METHODS;
                    }
                }
                writer.append("<li>").append(JavadocUtil.signatureToJenkinsdocLink(deprecatedMethod)).append("</li>\n");
            }
            writer.append("</div>");
        }
    }

    @Override
    protected void generateJsonReport(Writer writer) throws IOException {
        JSONObject obj = new JSONObject();

        {
            SortedSet<String> unusedClasses = new TreeSet<>();
            CLASSES:
            for (String deprecatedClass : new TreeSet<>(api.getClasses())) {
                if (!isRelevantSignature(deprecatedClass)) {
                    continue;
                }
                for (DeprecatedUsage usage : usages) {
                    if (usage.getClasses().contains(deprecatedClass)) {
                        continue CLASSES;
                    }
                }
                unusedClasses.add(deprecatedClass);
            }
            obj.put("classes", unusedClasses);
        }

        {
            SortedSet<String> unusedFields = new TreeSet<>();
            FIELDS:
            for (String deprecatedField : new TreeSet<>(api.getFields())) {
                if (!isRelevantSignature(deprecatedField)) {
                    continue;
                }
                for (DeprecatedUsage usage : usages) {
                    if (usage.getFields().contains(deprecatedField)) {
                        continue FIELDS;
                    }
                }
                unusedFields.add(deprecatedField);
            }
            obj.put("fields", unusedFields);
        }

        {
            SortedSet<String> unusedMethods = new TreeSet<>();
            METHODS:
            for (String deprecatedMethod : api.getMethods()) {
                if (!isRelevantSignature(deprecatedMethod)) {
                    continue;
                }
                for (DeprecatedUsage usage : usages) {
                    if (usage.getMethods().contains(deprecatedMethod)) {
                        continue METHODS;
                    }
                }
                unusedMethods.add(deprecatedMethod);
            }
            obj.put("methods", unusedMethods);
        }

        writer.append(obj.toString(2));
    }

    private boolean isRelevantSignature(String signature) {
        if (signature.contains("jenkins")) {
            return true;
        }
        if (signature.contains("hudson")) {
            return true;
        }
        if (signature.contains("org/kohsuke")) {
            return true;
        }
        return false;
    }
}
