package org.jenkinsci.deprecatedusage.report;

import org.jenkinsci.deprecatedusage.Report;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class RecursiveUsageByPluginOnlyMethodsReport extends Report {
    private LevelReportStorage levelReportStorage;

    public RecursiveUsageByPluginOnlyMethodsReport(LevelReportStorage levelReportStorage, File outputDir, String reportName) {
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
            map.put(pluginName, new JSONArray(pluginMethods));
        });

        writer.append(map.toString(2));
    }
}
