package org.jenkinsci.deprecatedusage.report;

import org.jenkinsci.deprecatedusage.Report;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecursiveUsageByPluginFlatReducedReport extends Report {
    private LevelReportStorage levelReportStorage;

    public RecursiveUsageByPluginFlatReducedReport(LevelReportStorage levelReportStorage, File outputDir, String reportName) {
        super(null, null, outputDir, reportName);
        this.levelReportStorage = levelReportStorage;
    }

    @Override
    protected void generateHtmlReport(Writer writer) throws IOException {
    }

    @Override
    protected void generateJsonReport(Writer writer) throws IOException {
        JSONObject map = JsonHelper.createOrderedJSONObject();

        List<String> pluginNames = new ArrayList<>(levelReportStorage.pluginsToMethods.keySet());
        pluginNames.sort(String::compareToIgnoreCase);

        pluginNames.forEach(pluginName -> {
            List<String> pluginMethods = new ArrayList<>(levelReportStorage.pluginsToMethods.get(pluginName));
            pluginMethods.sort(String::compareToIgnoreCase);
            
            JSONObject methodHierarchies = JsonHelper.createOrderedJSONObject();

            Set<String> allSecondaryMethods = new HashSet<>();
            pluginMethods.forEach(method -> {
                collectCallHierarchy(method, allSecondaryMethods);
            });
            pluginMethods.forEach(method -> {
                String methodNameAndPlugin = levelReportStorage.getPluginSourceForMethod(method);
                methodHierarchies.put(methodNameAndPlugin, computeCallHierarchy(method, allSecondaryMethods));
            });

            map.put(pluginName, methodHierarchies);
        });

        writer.append(map.toString(2));
    }

    private Object computeCallHierarchy(String method, Set<String> allSecondaryMethods) {
        return computeCallHierarchy(method, 0, allSecondaryMethods, new HashSet<>());
    }

    private Object computeCallHierarchy(String method, int level, Set<String> allSecondaryMethods, Set<String> alreadyVisitedThisTime) {
        if (level == 0 && allSecondaryMethods.contains(method)) {
            // their stack is already included somewhere else in this plugin
            return "<secondary-method>";
        }
        if (alreadyVisitedThisTime.contains(method)) {
            // their stack is already included somewhere else in this method hierarchy
            return "<already-included>";
        }

        alreadyVisitedThisTime.add(method);

        Set<String> callers = levelReportStorage.globalConsumerToProviders.get(method);
        if (callers == null) {
            return "";
        } else {
            List<String> orderedCallers = new ArrayList<>(callers);
            orderedCallers.sort(String::compareToIgnoreCase);

            JSONObject result = new JSONObject();

            orderedCallers.forEach(s -> {
                String methodNameAndPlugin = levelReportStorage.getPluginSourceForMethod(s);
                result.put(methodNameAndPlugin, computeCallHierarchy(s, level + 1, allSecondaryMethods, alreadyVisitedThisTime));
            });

            return result;
        }
    }

    private void collectCallHierarchy(String method, Set<String> allSecondaryMethods) {
        collectCallHierarchy(method, 0, allSecondaryMethods);
    }

    private void collectCallHierarchy(String method, int level, Set<String> allSecondaryMethods) {
        if (allSecondaryMethods.contains(method)) {
            return;
        }

        if (level != 0) {
            allSecondaryMethods.add(method);
        }

        Set<String> callers = levelReportStorage.globalConsumerToProviders.get(method);
        if (callers != null) {
            callers.forEach(s -> {
                collectCallHierarchy(s, level + 1, allSecondaryMethods);
            });
        }
    }
}
