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
import java.net.URI;
import java.nio.charset.StandardCharsets;
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

        options.buildCache();

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
                URI uri = new URI(updateCenterURL);
                executor.execute(() -> {
                    System.out.println("Using update center URL: " + updateCenterURL);
                    try {
                        String json = IOUtils.toString(uri, StandardCharsets.UTF_8).replace("updateCenter.post(", "");
                        UpdateCenter updateCenter = new UpdateCenter(new JSONObject(json));
                        if (updateCenter.getCore() != null) {
                            cores.add(updateCenter.getCore());
                        }
                        plugins.addAll(updateCenter.getPlugins().stream().filter(f -> Options.get().shouldScanPlugin(f.getName())).collect(Collectors.toSet()));
                        metadataLoaded.countDown();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }

            // wait for async code to finish submitting
            metadataLoaded.await(10, TimeUnit.SECONDS);

            Collection<JenkinsFile> downloadedCores;
            if (options.skipDownloads) {
                System.out.println("Checking core files");
                downloadedCores = downloader.useExistingFiles(cores);
            } else {
                System.out.println("Downloading core files");
                downloadedCores = downloader.synchronize(cores).get();
            }

            if (!options.onlyIncludeSpecified) {
                for (JenkinsFile core : downloadedCores) {
                    try {
                        System.out.println("Analyzing deprecated APIs in " + core);
                        deprecatedApi.analyze(core.getFile());
                        System.out.println("Finished deprecated API analysis in " + core);
                    } catch (IOException e) {
                        System.out.println("Error analyzing deprecated APIs in " + core);
                        System.out.println(e);
                    }
                }
            }

            if (options.limitPluginsFile != null) {
                int previousSize = plugins.size();
                Set<String> limitedScopeOfPlugins = Options.getLimitedScopeOfPlugins();
                plugins.removeIf(jenkinsFile -> !limitedScopeOfPlugins.contains(jenkinsFile.getName()));
                int afterSize = plugins.size();

                System.out.println("By using a limited scope of plugins, the list of plugins went from " + previousSize + " to " + afterSize);
            }

            Collection<JenkinsFile> downloadedPlugins;
            if (options.skipDownloads) {
                System.out.println("Checking plugin files (out of " + plugins.size() + " total)");
                downloadedPlugins = downloader.useExistingFiles(plugins);
            } else {
                System.out.println("Downloading plugin files (out of " + plugins.size() + " total)");
                downloadedPlugins = downloader.synchronize(plugins).get();
            }

            System.out.println("Analyzing usage in plugins");
            SearchCriteria deprecatedAndOptionCriteria = new OptionsBasedSearchCriteria().combineWith(new DeprecatedApiSearchCriteria(deprecatedApi));

            List<DeprecatedUsage> deprecatedUsages;
            if (options.includeCore) {
                List<DeprecatedUsage> fromCores = analyzeDeprecatedUsage(downloadedCores, deprecatedAndOptionCriteria, executor, options.includeCoreLibraries);
                List<DeprecatedUsage> fromPlugins = analyzeDeprecatedUsage(downloadedPlugins, deprecatedAndOptionCriteria, executor, options.includePluginLibraries);

                List<DeprecatedUsage> all = new ArrayList<>(fromPlugins);
                all.addAll(fromCores);
                deprecatedUsages = all;
            } else {
                deprecatedUsages = analyzeDeprecatedUsage(downloadedPlugins, deprecatedAndOptionCriteria, executor, options.includePluginLibraries);
            }

            System.out.println("Initial analysis done");
            List<Report> reports = new ArrayList<>();
            reports.add(new DeprecatedUsageByPluginReport(deprecatedApi, deprecatedUsages, new File("output"), "usage-by-plugin"));
            reports.add(new DeprecatedUnusedApiReport(deprecatedApi, deprecatedUsages, new File("output"), "deprecated-and-unused"));
            reports.add(new DeprecatedUsageByApiReport(deprecatedApi, deprecatedUsages, new File("output"), "usage-by-api"));

            for (Report report : reports) {
                report.generateJsonReport();
                report.generateHtmlReport();
            }

            if (options.recursive) {
                System.out.println("Entering recursive mode");
                reports = new ArrayList<>();
                LevelReportStorage levelReportStorage = new LevelReportStorage();

                loopForRecursiveSearch(deprecatedUsages, levelReportStorage, newMethodsFound -> {
                    RecursiveSearchCriteria recursiveSearchCriteria = new RecursiveSearchCriteria(newMethodsFound);
                    if (options.includeCore) {
                        List<DeprecatedUsage> fromCores = analyzeDeprecatedUsage(downloadedCores, recursiveSearchCriteria, executor, options.includeCoreLibraries);
                        List<DeprecatedUsage> fromPlugins = analyzeDeprecatedUsage(downloadedPlugins, recursiveSearchCriteria, executor, options.includePluginLibraries);

                        List<DeprecatedUsage> all = new ArrayList<>(fromPlugins);
                        all.addAll(fromCores);
                        return all;
                    } else {
                        return analyzeDeprecatedUsage(downloadedPlugins, recursiveSearchCriteria, executor, options.includePluginLibraries);
                    }
                });

                reports.add(new RecursiveUsageByPluginByLevelReport(levelReportStorage, new File("output"), "recursive-usage-plugin-level"));
                reports.add(new RecursiveUsageByPluginFlatReport(levelReportStorage, new File("output"), "recursive-usage-flat"));
                reports.add(new RecursiveUsageByPluginOnlyMethodsReport(levelReportStorage, new File("output"), "recursive-usage-only-methods"));
                reports.add(new RecursiveUsageByPluginFlatReducedReport(levelReportStorage, new File("output"), "recursive-usage-flat-reduced"));

                for (Report report : reports) {
                    report.generateJsonReport();
                    report.generateHtmlReport();
                }
            }

            System.out.println("duration : " + (System.currentTimeMillis() - start) + " ms at "
                    + DateFormat.getDateTimeInstance().format(new Date()));
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Not a recursion itself to prevent memory issue
     */
    private void loopForRecursiveSearch(List<DeprecatedUsage> previousUsages, LevelReportStorage levelReportStorage, Function<Set<String>, List<DeprecatedUsage>> func) {
        int level = 1;

        List<DeprecatedUsage> currUsages = previousUsages;
        Set<String> allMethodKeys = new HashSet<>();

        boolean running = true;
        while (running) {
            System.out.println();
            System.out.print("Level " + level + " done");
            Set<String> newMethodsFound = computeNewMethodKeys(currUsages, allMethodKeys);

            if (!newMethodsFound.isEmpty()) {
                System.out.print(", new methods found = " + newMethodsFound.size());
                levelReportStorage.addLevel(level, currUsages);

                // a new method that is never used as a provider will not impact globalXxxToYyy
                System.out.print(", total consumers found = " + levelReportStorage.globalConsumerToProviders.size());
                System.out.println(", total providers found = " + levelReportStorage.globalProviderToConsumers.size());
                System.out.println();

                if (level < Options.get().recursiveMaxDepth) {
                    level++;

                    currUsages = func.apply(newMethodsFound);
                } else {
                    System.out.println("Max depth (" + Options.get().recursiveMaxDepth + ") reached.");
                    running = false;
                }
            } else {
                System.out.println(", without new methods");
                running = false;
            }
        }
    }

    private Set<String> computeNewMethodKeys(List<DeprecatedUsage> previousUsages, Set<String> allMethodKeys) {
        Set<String> methodsFound = new HashSet<>();
        for (DeprecatedUsage recursiveUsage : previousUsages) {
            methodsFound.addAll(recursiveUsage.getNewSignatures());
        }

        Set<String> newMethodsFound = methodsFound.stream().filter(s -> !allMethodKeys.contains(s)).collect(Collectors.toSet());
        allMethodKeys.addAll(newMethodsFound);

        return newMethodsFound;
    }

    /**
     * Adds hardcoded classes to analyze for usage. This is mostly designed for finding classes planned for deprecation,
     * but can be also used to find any class usage.
     */
    private static void addClassesToAnalyze(DeprecatedApi deprecatedApi) {
        if (Options.get().additionalClassesFile != null) {
            deprecatedApi.addClasses(Options.getAdditionalClasses());
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
                    System.out.println("deleting " + plugin + " and skipping, because " + e);
                    try {
                        plugin.deleteFile();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                } catch (final Exception e) {
                    System.out.println(e + " on " + plugin.getFile().getName());
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
