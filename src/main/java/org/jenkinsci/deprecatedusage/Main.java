package org.jenkinsci.deprecatedusage;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.exception.RemotelyClosedException;
import org.asynchttpclient.filter.FilterContext;
import org.asynchttpclient.filter.FilterException;
import org.asynchttpclient.filter.IOExceptionFilter;
import org.asynchttpclient.filter.ResponseFilter;
import org.json.JSONObject;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipException;

import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.config;

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
            Logger l = Logger.getLogger("org.asynchttpclient");
            l.setLevel(Level.ALL);
            ConsoleHandler h = new ConsoleHandler();
            h.setLevel(Level.ALL);
            l.addHandler(h);
            /* or turn on all of org.apache.hc.client5.http but exclude:
            Logger.getLogger("org.apache.hc.client5.http.wire").setLevel(Level.INFO);
            */
        }

        final long start = System.currentTimeMillis();
        try (AsyncHttpClient client = asyncHttpClient(config()
                .setMaxRequestRetry(3)
                .setRequestTimeout(300_000)
                .addIOExceptionFilter(new IOExceptionFilter() {
                    @Override
                    public <T> FilterContext<T> filter(FilterContext<T> ctx) throws FilterException {
                        IOException exception = ctx.getIOException();
                        // flaky update centers
                        if (exception instanceof RemotelyClosedException) {
                            return new FilterContext.FilterContextBuilder<>(ctx).replayRequest(true).build();
                        } else {
                            return ctx;
                        }
                    }
                })
                .addResponseFilter(new ResponseFilter() {
                    @Override
                    public <T> FilterContext<T> filter(FilterContext<T> ctx) throws FilterException {
                        // another type of flaky update center
                        if (ctx.getResponseStatus().getStatusCode() == HttpURLConnection.HTTP_BAD_GATEWAY) {
                            return new FilterContext.FilterContextBuilder<>(ctx).replayRequest(true).build();
                        } else {
                            return ctx;
                        }
                    }
                })
                .setFollowRedirect(true))) {
            final DeprecatedApi deprecatedApi = new DeprecatedApi();
            addClassesToAnalyze(deprecatedApi);
            List<String> updateCenterURLs = options.getUpdateCenterURLs();
            List<CompletableFuture<Void>> metadataLoaded = new ArrayList<>(updateCenterURLs.size());
            Set<JenkinsFile> cores = new ConcurrentSkipListSet<>(Comparator.comparing(JenkinsFile::getFile));
            Set<JenkinsFile> plugins = new ConcurrentSkipListSet<>(Comparator.comparing(JenkinsFile::getFile));
            for (String updateCenterURL : updateCenterURLs) {
                System.out.println("Using update center URL: " + updateCenterURL);
                metadataLoaded.add(client.prepareGet(updateCenterURL)
                        .execute()
                        .toCompletableFuture()
                        .thenAccept(response -> {
                            String json = response.getResponseBody().replace("updateCenter.post(", "");
                            JSONObject jsonRoot = new JSONObject(json);
                            UpdateCenter updateCenter = new UpdateCenter(jsonRoot);
                            cores.add(updateCenter.getCore());
                            plugins.addAll(updateCenter.getPlugins());
                        }));
            }

            // wait for async code to finish submitting
            CompletableFuture.allOf(metadataLoaded.toArray(new CompletableFuture[0])).join();
            System.out.println("Downloading core files");
            CompletableFuture<Void> coreAnalysisComplete = CompletableFuture.allOf(
                    cores.stream()
                            .map(core -> core.downloadIfNotExists(client).thenRun(() -> {
                                try {
                                    System.out.println("Analyzing deprecated APIs in " + core);
                                    deprecatedApi.analyze(core.getFile());
                                    System.out.println("Finished deprecated API analysis in " + core);
                                } catch (IOException e) {
                                    System.out.println("Error analyzing deprecated APIs in " + core);
                                    System.out.println(e.toString());
                                }
                            }))
                            .toArray(CompletableFuture[]::new));

            System.out.println("Downloading plugin files");
            int pluginCount = plugins.size();
            int maxConcurrent = options.maxConcurrentDownloads;
            Semaphore concurrentDownloadsPermit = new Semaphore(maxConcurrent);
            List<JenkinsFile> downloadedPlugins = Collections.synchronizedList(new ArrayList<>(pluginCount));
            List<CompletableFuture<?>> futures = new ArrayList<>(pluginCount + 1);
            futures.add(coreAnalysisComplete);
            for (JenkinsFile plugin : plugins) {
                concurrentDownloadsPermit.acquire();
                futures.add(plugin.downloadIfNotExists(client).handle((success, failure) -> {
                    concurrentDownloadsPermit.release();
                    if (failure != null) {
                        if (failure instanceof RemotelyClosedException) {
                            System.out.println("Gave up trying to download " + plugin);
                        } else {
                            System.out.println("Error downloading " + plugin);
                            System.out.println(failure.toString());
                        }
                    } else {
                        downloadedPlugins.add(plugin);
                    }
                    return null;
                }));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            System.out.println("Analyzing usage in plugins");
            final List<DeprecatedUsage> deprecatedUsages = analyzeDeprecatedUsage(downloadedPlugins, deprecatedApi, new ForkJoinPool());

            Report[] reports = new Report[]{
                    new DeprecatedUsageByPluginReport(deprecatedApi, deprecatedUsages, new File("output"), "usage-by-plugin"),
                    new DeprecatedUnusedApiReport(deprecatedApi, deprecatedUsages, new File("output"), "deprecated-and-unused"),
                    new DeprecatedUsageByApiReport(deprecatedApi, deprecatedUsages, new File("output"), "usage-by-api")
            };

            for (Report report : reports) {
                report.generateJsonReport();
                report.generateHtmlReport();
            }

            System.out.println("duration : " + (System.currentTimeMillis() - start) + " ms at "
                    + DateFormat.getDateTimeInstance().format(new Date()));
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

    private static List<DeprecatedUsage> analyzeDeprecatedUsage(List<JenkinsFile> plugins, DeprecatedApi deprecatedApi,
                                                                Executor executor)
            throws InterruptedException, ExecutionException {
        List<CompletableFuture<DeprecatedUsage>> futures = new ArrayList<>();
        for (JenkinsFile plugin : plugins) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                DeprecatedUsage deprecatedUsage = new DeprecatedUsage(plugin.getName(), plugin.getVersion(), deprecatedApi);
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
        return deprecatedUsages;
    }

}
