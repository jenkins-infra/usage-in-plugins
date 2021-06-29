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

public class RecursiveUsageByPluginFlatReport extends Report {
    private LevelReportStorage levelReportStorage;

    public RecursiveUsageByPluginFlatReport(LevelReportStorage levelReportStorage, File outputDir, String reportName) {
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
            JSONObject methodHierarchies = JsonHelper.createOrderedJSONObject();

            List<String> pluginMethods = new ArrayList<>(levelReportStorage.pluginsToMethods.get(pluginName));
            pluginMethods.sort(String::compareToIgnoreCase);
            pluginMethods.forEach(method -> {
                String methodNameAndPlugin = levelReportStorage.getPluginSourceForMethod(method);
                methodHierarchies.put(methodNameAndPlugin, computeCallHierarchy(method));
            });

            map.put(pluginName, methodHierarchies);
        });

        writer.append(map.toString(2));
    }

    private Object computeCallHierarchy(String method) {
        return computeCallHierarchy(method, 0, new HashSet<>());
    }

    private Object computeCallHierarchy(String method, int level, Set<String> alreadyVisited) {
        if (alreadyVisited.contains(method)) {
            return "<already-included>";
        }

        alreadyVisited.add(method);

        Set<String> callers = levelReportStorage.globalConsumerToProviders.get(method);
        if (callers == null) {
            return "";
        } else {
            List<String> orderedCallers = new ArrayList<>(callers);
            orderedCallers.sort(String::compareToIgnoreCase);

            JSONObject result = new JSONObject();

            orderedCallers.forEach(s -> {
                String methodNameAndPlugin = levelReportStorage.getPluginSourceForMethod(s);
                result.put(methodNameAndPlugin, computeCallHierarchy(s, level + 1, alreadyVisited));
            });

            return result;
        }
    }
}
