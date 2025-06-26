package org.jenkinsci.deprecatedusage.report;

import org.jenkinsci.deprecatedusage.DeprecatedUsage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Main idea
 * The "provider" methods are the ones specified in the additionalMethodFile option.
 * A method calling a provider in its body is a "consumer".
 * And then, in the next level of recursion, the consumer becomes a provider for others (indirect provider).
 */
public class LevelReportStorage {
    /* Hacky way to store the information, with risk of modification by other instances */

    // it is assumed that a method full signature is unique
    /**
     * List of methods in a particular plugin
     */
    public final Map<String, Set<String>> pluginsToMethods = new HashMap<>();
    /**
     * Link back the methods to their plugin
     */
    public final Map<String, String> methodToPlugin = new HashMap<>();
    /**
     * The different levels a method was found at
     */
    public final Map<String, Set<Integer>> methodToLevels = new HashMap<>();

    // To rebuild the dependency tree
    public final Map<String, Set<String>> globalConsumerToProviders = new HashMap<>();
    public final Map<String, Set<String>> globalProviderToConsumers = new HashMap<>();

    public LevelReportStorage() {
    }

    public void addLevel(int level, List<DeprecatedUsage> usages) {
        usages.forEach(u -> {
            String pluginName = u.getPlugin().artifactId();

            Map<String, Set<String>> currPluginProviderToConsumers = new HashMap<>(u.getProviderToConsumers());
            Map<String, Set<String>> currPluginConsumerToProviders = new HashMap<>(u.getConsumerToProviders());
            if (!currPluginProviderToConsumers.isEmpty()) {
                // kept as a "for i" format to ease debug in case of very large number of occurrences
                List<String> keys = new ArrayList<>(currPluginProviderToConsumers.keySet());
                for (String provider : keys) {
                    Set<String> consumers = currPluginProviderToConsumers.get(provider);
                    globalProviderToConsumers.computeIfAbsent(provider, s -> new HashSet<>()).addAll(consumers);

                    consumers.forEach(consumer -> {
                        methodToLevels.computeIfAbsent(consumer, s -> new HashSet<>()).add(level);
                        pluginsToMethods.computeIfAbsent(pluginName, s -> new HashSet<>()).add(consumer);
                        methodToPlugin.put(consumer, pluginName);

                        currPluginConsumerToProviders.computeIfAbsent(consumer, s -> new HashSet<>())
                                .add(provider);
                        globalConsumerToProviders.computeIfAbsent(consumer, s -> new HashSet<>())
                                .add(provider);
                    });
                }
            }
        });
    }

    public String getPluginSourceForMethod(String methodSignature) {
        String ownerPlugin = methodToPlugin.get(methodSignature);
        String methodNameAndPlugin = (ownerPlugin != null ? ownerPlugin : "<other>") + " " + methodSignature;
        return methodNameAndPlugin;
    }
}
