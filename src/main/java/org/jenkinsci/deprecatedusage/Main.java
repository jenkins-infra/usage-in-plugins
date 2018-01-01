package org.jenkinsci.deprecatedusage;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipException;

public class Main {
    private static final String UPDATE_CENTER_URL =
    // "http://updates.jenkins-ci.org/experimental/update-center.json";
    "http://updates.jenkins-ci.org/update-center.json";

    public static void main(String[] args) throws Exception {
        final long start = System.currentTimeMillis();
        final UpdateCenter updateCenter = new UpdateCenter(new URL(UPDATE_CENTER_URL));
        System.out.println("Downloaded update-center.json");
        updateCenter.download();
        System.out.println("All files are up to date (" + updateCenter.getPlugins().size() + " plugins)");

        createReport(updateCenter, new JenkinsCoreAnalysis());
        for(JenkinsFile plugin : updateCenter.getPlugins()) {
            createReport(updateCenter, new PluginAnalysis(plugin));
        }

        System.out.println("duration : " + (System.currentTimeMillis() - start) + " ms at "
                + DateFormat.getDateTimeInstance().format(new Date()));
    }

    private static void createReport(UpdateCenter updateCenter, Analysis analysis) throws IOException, InterruptedException, ExecutionException {
        System.out.println("Analyzing deprecated api in " + analysis.getAnalyzedFileName());
        final File analyzedFile = analysis.getAnalyzedFile(updateCenter).getFile();
        final DeprecatedApi deprecatedApi = new DeprecatedApi();
        deprecatedApi.analyze(analyzedFile);

        System.out.println("Analyzing deprecated usage in " + analysis.getDependentFilesName());
        final List<DeprecatedUsage> deprecatedUsages = analyzeDeprecatedUsage(analysis.getDependentFiles(updateCenter), deprecatedApi);

        File outputDir = analysis.getOutputDirectory("output");
        Report[] reports = new Report[] {
                new DeprecatedUsageByPluginReport(deprecatedApi, deprecatedUsages, outputDir, "usage-by-plugin"),
                new DeprecatedUnusedApiReport(deprecatedApi, deprecatedUsages, outputDir, "deprecated-and-unused"),
                new DeprecatedUsageByApiReport(deprecatedApi, deprecatedUsages, outputDir, "usage-by-api")
        };

        for (Report report : reports) {
            report.generateJsonReport();
            report.generateHtmlReport();
        }
    }

    private static List<DeprecatedUsage> analyzeDeprecatedUsage(List<JenkinsFile> plugins,
            final DeprecatedApi deprecatedApi) throws InterruptedException, ExecutionException {
        final ExecutorService executorService = Executors
                .newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        final List<Future<DeprecatedUsage>> futures = new ArrayList<>(plugins.size());
        for (final JenkinsFile plugin : plugins) {
            final Callable<DeprecatedUsage> task = new Callable<DeprecatedUsage>() {
                @Override
                public DeprecatedUsage call() throws IOException {
                    final DeprecatedUsage deprecatedUsage = new DeprecatedUsage(plugin.getName(),
                            plugin.getVersion(), deprecatedApi);
                    try {
                        deprecatedUsage.analyze(plugin.getFile());
                    } catch (final EOFException | ZipException e) {
                        System.out.println("deleting " + plugin.getFile().getName() + " and skipping, because "
                                + e.toString());
                        plugin.getFile().delete();
                    } catch (final Exception e) {
                        System.out.println(e.toString() + " on " + plugin.getFile().getName());
                        e.printStackTrace();
                    }
                    return deprecatedUsage;
                }
            };
            futures.add(executorService.submit(task));
        }

        final List<DeprecatedUsage> deprecatedUsages = new ArrayList<>();
        int i = 0;
        for (final Future<DeprecatedUsage> future : futures) {
            final DeprecatedUsage deprecatedUsage = future.get();
            deprecatedUsages.add(deprecatedUsage);
            i++;
            if (i % 10 == 0) {
                System.out.print(".");
            }
            if (i % 100 == 0) {
                System.out.print(" ");
            }
            if (i % 500 == 0) {
                System.out.print("\n");
            }
        }
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
        // wait for threads to stop
        Thread.sleep(100);
        return deprecatedUsages;
    }
}
