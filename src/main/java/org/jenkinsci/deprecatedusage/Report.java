package org.jenkinsci.deprecatedusage;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public abstract class Report {
    protected final DeprecatedApi api;
    protected final List<DeprecatedUsage> usages;
    protected final File outputDir;
    protected final String reportName;

    public Report(DeprecatedApi api, List<DeprecatedUsage> usages, File outputDir, String reportName) {
        this.api = api;
        this.usages = usages;
        this.outputDir = outputDir;
        this.reportName = reportName;
    }

    protected abstract void generateHtmlReport(Writer writer) throws IOException;
    protected abstract void generateJsonReport(Writer writer) throws IOException;

    public void generateJsonReport() throws IOException {
        outputDir.mkdirs();
        File file = new File(outputDir, reportName + ".json");
        System.out.println("Writing " + file);

        try (FileWriter writer = new FileWriter(file)) {
            generateJsonReport(writer);
        }
    }

    public void generateHtmlReport() throws IOException {
        outputDir.mkdirs();
        File file = new File(outputDir, reportName + ".html");
        System.out.println("Writing " + file);

        try (FileWriter writer = new FileWriter(new File(outputDir, reportName + ".html"))) {
            IOUtils.copy(this.getClass().getResourceAsStream("/report-header.html"), writer, StandardCharsets.UTF_8);
            generateHtmlReport(writer);
            IOUtils.copy(this.getClass().getResourceAsStream("/report-footer.html"), writer, StandardCharsets.UTF_8);
        }
    }
}
