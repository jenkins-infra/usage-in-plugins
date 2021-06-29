package org.jenkinsci.deprecatedusage;

import org.apache.commons.io.IOUtils;
import org.jenkinsci.deprecatedusage.report.DeprecatedUnusedApiReport;
import org.jenkinsci.deprecatedusage.report.DeprecatedUsageByApiReport;
import org.jenkinsci.deprecatedusage.report.DeprecatedUsageByPluginReport;
import org.jenkinsci.deprecatedusage.report.LevelReportStorage;
import org.jenkinsci.deprecatedusage.report.RecursiveUsageByPluginByLevelReport;
import org.jenkinsci.deprecatedusage.report.RecursiveUsageByPluginFlatReducedReport;
import org.jenkinsci.deprecatedusage.report.RecursiveUsageByPluginFlatReport;
import org.jenkinsci.deprecatedusage.report.RecursiveUsageByPluginOnlyMethodsReport;
import org.jenkinsci.deprecatedusage.search.DeprecatedApiSearchCriteria;
import org.jenkinsci.deprecatedusage.search.OptionsBasedSearchCriteria;
import org.jenkinsci.deprecatedusage.search.RecursiveSearchCriteria;
import org.jenkinsci.deprecatedusage.search.SearchCriteria;
import org.json.JSONObject;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipException;

public class Main {

    public static void main(String[] args) throws Exception {
        new Main().doMain(args);
    }

    public void doMain(String[] args) throws Exception {

        final Options options = Options.get();
        final CmdLineParser commandLineParser = new CmdLineParser(options);
        try {
            commandLineParser.parseArgument(args);
        } catch (CmdLineException e) {
            commandLineParser.printUsage(System.err);
            System.exit(1);
        }

        if (options.help) {
            commandLineParser.printUsage(System.err);
            System.exit(0);
        }

        if (options.verbose) {
            Logger l = Logger.getLogger("sun.net.www");
            l.setLevel(Level.ALL);
            ConsoleHandler h = new ConsoleHandler();
            h.setLevel(Level.ALL);
            l.addHandler(h);
        }

        final ExecutorService executor = Executors.newWorkStealingPool();
        final Downloader downloader = new Downloader(executor, options.maxConcurrentDownloads);
        final long start = System.currentTimeMillis();
        try {
            final DeprecatedApi deprecatedApi = new DeprecatedApi();
            addClassesToAnalyze(deprecatedApi);
            List<String> updateCenterURLs = options.getUpdateCenterURLs();
            CountDownLatch metadataLoaded = new CountDownLatch(updateCenterURLs.size());
            Set<JenkinsFile> cores = new ConcurrentSkipListSet<>(Comparator.comparing(JenkinsFile::getFile));
            Set<JenkinsFile> plugins = new ConcurrentSkipListSet<>(Comparator.comparing(JenkinsFile::getFile));
            for (String updateCenterURL : updateCenterURLs) {
                URL url = new URL(updateCenterURL);
                executor.execute(() -> {
                    System.out.println("Using update center URL: " + updateCenterURL);
                    try {
                        String json = IOUtils.toString(url, StandardCharsets.UTF_8).replace("updateCenter.post(", "");
                        UpdateCenter updateCenter = new UpdateCenter(new JSONObject(json));
                        cores.add(updateCenter.getCore());
                        plugins.addAll(updateCenter.getPlugins());
                        metadataLoaded.countDown();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }

            // wait for async code to finish submitting
            metadataLoaded.await(10, TimeUnit.SECONDS);
            System.out.println("Downloading core files");

            Collection<JenkinsFile> downloadedCores;
            if (options.skipDownloads) {
                downloadedCores = downloader.useExistingFiles(cores);
            } else {
                downloadedCores = downloader.synchronize(cores).get();
            }
            
            for (JenkinsFile core : downloadedCores) {
                try {
                    System.out.println("Analyzing deprecated APIs in " + core);
                    deprecatedApi.analyze(core.getFile());
                    System.out.println("Finished deprecated API analysis in " + core);
                } catch (IOException e) {
                    System.out.println("Error analyzing deprecated APIs in " + core);
                    System.out.println(e.toString());
                }
            }

            System.out.println("Downloading plugin files (out of " + plugins.size() + " total)");

            Collection<JenkinsFile> downloadedPlugins;
            if (options.skipDownloads) {
                downloadedPlugins = downloader.useExistingFiles(plugins);
            } else {
                downloadedPlugins = downloader.synchronize(plugins).get();
            }

            System.out.println("Analyzing usage in plugins");
            SearchCriteria deprecatedAndOptionCriteria = new OptionsBasedSearchCriteria().combineWith(new DeprecatedApiSearchCriteria(deprecatedApi));
            
            final List<DeprecatedUsage> deprecatedUsages = analyzeDeprecatedUsage(downloadedPlugins, deprecatedAndOptionCriteria, executor, options.includePluginLibraries);

            List<Report> reports = new ArrayList<>();
            reports.add(new DeprecatedUsageByPluginReport(deprecatedApi, deprecatedUsages, new File("output"), "usage-by-plugin"));
            reports.add(new DeprecatedUnusedApiReport(deprecatedApi, deprecatedUsages, new File("output"), "deprecated-and-unused"));
            reports.add(new DeprecatedUsageByApiReport(deprecatedApi, deprecatedUsages, new File("output"), "usage-by-api"));

            if (options.recursive) {
                LevelReportStorage levelReportStorage = new LevelReportStorage();

                // to prevent looping
                Set<String> allMethodKeys = new HashSet<>();
                
                recursiveLoop(1, deprecatedUsages, levelReportStorage, allMethodKeys, newMethodsFound -> {
                    RecursiveSearchCriteria recursiveSearchCriteria = new RecursiveSearchCriteria(newMethodsFound);
                    return analyzeDeprecatedUsage(downloadedPlugins, recursiveSearchCriteria, executor, options.includePluginLibraries);
                });

                reports.add(new RecursiveUsageByPluginByLevelReport(levelReportStorage, new File("output"), "recursive-usage-plugin-level"));
                reports.add(new RecursiveUsageByPluginFlatReport(levelReportStorage, new File("output"), "recursive-usage-flat"));
                reports.add(new RecursiveUsageByPluginOnlyMethodsReport(levelReportStorage, new File("output"), "recursive-usage-only-methods"));
                reports.add(new RecursiveUsageByPluginFlatReducedReport(levelReportStorage, new File("output"), "recursive-usage-flat-reduced"));
            }
            
            for (Report report : reports) {
                report.generateJsonReport();
                report.generateHtmlReport();
            }

            System.out.println("duration : " + (System.currentTimeMillis() - start) + " ms at "
                    + DateFormat.getDateTimeInstance().format(new Date()));
        } finally {
            executor.shutdown();
        }
    }
    
    private void recursiveLoop(int level, List<DeprecatedUsage> previousUsages, LevelReportStorage levelReportStorage, Set<String> allMethodKeys, Function<Set<String>, List<DeprecatedUsage>> func) {
        Set<String> methodsFound = new HashSet<>();
        for (DeprecatedUsage recursiveUsage : previousUsages) {
            methodsFound.addAll(recursiveUsage.getNewSignatures());
        }

        Set<String> newMethodsFound = methodsFound.stream().filter(s -> !allMethodKeys.contains(s)).collect(Collectors.toSet());
        allMethodKeys.addAll(newMethodsFound);

        if (newMethodsFound.size() > 0) {
            System.out.println();
            System.out.println("Level " + level + " done, found = " + newMethodsFound.size());
            System.out.println();

            levelReportStorage.addLevel(level, previousUsages);
            
            if (level < Options.get().recursiveMaxDepth) {
                List<DeprecatedUsage> currUsages = func.apply(newMethodsFound);

                recursiveLoop(level + 1, currUsages, levelReportStorage, allMethodKeys, func);
            }
        }
    }

    /**
     * Adds hardcoded classes to analyze for usage. This is mostly designed for finding classes planned for deprecation,
     * but can be also used to find any class usage.
     */
    private static void addClassesToAnalyze(DeprecatedApi deprecatedApi) {
        if (Options.get().additionalClassesFile != null) {
            List<String> additionalClasses = Options.getAdditionalClasses();
            deprecatedApi.addClasses(additionalClasses);
        } else {
            System.out.println("No 'additionalClassesFile' option, only already deprecated class will be searched for");
        }
    }

    private static List<DeprecatedUsage> analyzeDeprecatedUsage(Collection<JenkinsFile> plugins, SearchCriteria searchCriteria,
                                                                Executor executor, boolean scanPluginLibs) {
        List<CompletableFuture<DeprecatedUsage>> futures = new ArrayList<>();
        for (JenkinsFile plugin : plugins) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                DeprecatedUsage deprecatedUsage = new DeprecatedUsage(plugin.getName(), plugin.getVersion(), searchCriteria, scanPluginLibs);
                try {
                    deprecatedUsage.analyze(plugin.getFile());
                } catch (final EOFException | ZipException | FileNotFoundException e) {
                    System.out.println("deleting " + plugin + " and skipping, because " + e.toString());
                    try {
                        plugin.deleteFile();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                } catch (final Exception e) {
                    System.out.println(e.toString() + " on " + plugin.getFile().getName());
                    e.printStackTrace();
                }
                return deprecatedUsage;
            }, executor));
        }

        final List<DeprecatedUsage> deprecatedUsages = new ArrayList<>();
        int i = 0;
        for (final Future<DeprecatedUsage> future : futures) {
            final DeprecatedUsage deprecatedUsage;
            try {
                deprecatedUsage = future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
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
        return deprecatedUsages;
    }

}
