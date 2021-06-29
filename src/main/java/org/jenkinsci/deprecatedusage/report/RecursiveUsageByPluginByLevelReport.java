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

        List<String> pluginNames = new ArrayList<>(levelReportStorage.pluginsToLevelToProviderToConsumers.keySet());
        pluginNames.sort(String::compareToIgnoreCase);

        pluginNames.forEach(pluginName -> {
            Map<Integer, Map<String, Set<String>>> levelToMethodToCallers = levelReportStorage.pluginsToLevelToProviderToConsumers.get(pluginName);
            
            JSONObject pluginContent = JsonHelper.createOrderedJSONObject();
            
            List<Integer> levels = new ArrayList<>(levelToMethodToCallers.keySet());
            levels.sort(Integer::compareTo);

            levels.forEach(level -> {
                Map<String, Set<String>> methodToCallers = levelToMethodToCallers.get(level);
                
                // this could be refactored using the lower level Map instead of the multi-level one
                
                Map<String, List<String>> callerToMethods = new HashMap<>();
                methodToCallers.forEach((method, callers) -> {
                    callers.forEach(caller -> {
                        callerToMethods.computeIfAbsent(caller, s -> new ArrayList<>())
                                .add(method);
                    });
                });
                
                JSONObject methodContent = JsonHelper.createOrderedJSONObject();
                
                List<String> callers = new ArrayList<>(callerToMethods.keySet());
                callers.sort(String::compareToIgnoreCase);
                callers.forEach(caller -> {
                    // remove duplicate, like com/checkmarx/jenkins/CxCredentials#getCxCredentials calling twice com/checkmarx/jenkins/Aes#encrypt
                    List<String> methods = new ArrayList<>(new HashSet<>(callerToMethods.get(caller)));
                    methods.sort(String::compareToIgnoreCase);
                    methodContent.put(caller, methods);
                });
                
                pluginContent.put(level.toString(), methodContent);
            });
            
            map.put(pluginName, pluginContent);
        });
        
        writer.append(map.toString(2));
    }
}
