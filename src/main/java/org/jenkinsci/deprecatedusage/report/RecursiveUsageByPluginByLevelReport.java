package org.jenkinsci.deprecatedusage.report;

import org.jenkinsci.deprecatedusage.Report;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecursiveUsageByPluginByLevelReport extends Report {
    private LevelReportStorage levelReportStorage;

    public RecursiveUsageByPluginByLevelReport(LevelReportStorage levelReportStorage, File outputDir, String reportName) {
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
            Set<String> pluginMethods = levelReportStorage.pluginsToMethods.get(pluginName);
            Map<Integer, Set<String>> minLevelToMethods = new HashMap<>();
            pluginMethods.forEach(method -> {
                Set<Integer> levels = levelReportStorage.methodToLevels.get(method);
                Integer minLevel = levels.stream().min(Integer::compareTo).get();
                minLevelToMethods.computeIfAbsent(minLevel, s -> new HashSet<>()).add(method);
            });

            JSONObject pluginContent = JsonHelper.createOrderedJSONObject();

            List<Integer> levels = new ArrayList<>(minLevelToMethods.keySet());
            levels.sort(Integer::compareTo);

            levels.forEach(level -> {
                JSONObject methodContent = JsonHelper.createOrderedJSONObject();
                
                List<String> methodsAtCurrLevel = new ArrayList<>(minLevelToMethods.get(level));
                methodsAtCurrLevel.sort(String::compareToIgnoreCase);

                methodsAtCurrLevel.forEach(method -> {
                    List<String> providers = new ArrayList<>(levelReportStorage.globalConsumerToProviders.get(method));
                    providers.sort(String::compareToIgnoreCase);
                    methodContent.put(method, providers);
                });

                pluginContent.put(level.toString(), methodContent);
            });

            map.put(pluginName, pluginContent);
        });
        
        writer.append(map.toString(2));
    }
}
