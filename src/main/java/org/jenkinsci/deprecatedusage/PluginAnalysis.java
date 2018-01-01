package org.jenkinsci.deprecatedusage;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class PluginAnalysis implements Analysis {

    private final JenkinsFile plugin;

    public PluginAnalysis(JenkinsFile plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getAnalyzedFileName() {
        return plugin.getName() + " plugin";
    }

    @Override
    public JenkinsFile getAnalyzedFile(UpdateCenter updateCenter) {
        return plugin;
    }

    @Override
    public String getDependentFilesName() {
        return plugin.getName() + " plugin dependencies";
    }

    @Override
    public List<JenkinsFile> getDependentFiles(UpdateCenter updateCenter) {
        return updateCenter.getPlugins().stream()
            .filter(p -> p.isDependentOn(plugin))
            .collect(Collectors.toList());
    }

    @Override
    public File getOutputDirectory(String baseDir) {
        return new File(baseDir, plugin.getName());
    }
}
