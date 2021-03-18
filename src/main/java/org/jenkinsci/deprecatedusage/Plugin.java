package org.jenkinsci.deprecatedusage;

import java.util.Objects;

public class Plugin implements Comparable<Plugin> {
    public final String artifactId;
    public final String version;

    public Plugin(String artifactId, String version) {
        this.artifactId = Objects.requireNonNull(artifactId);
        this.version = Objects.requireNonNull(version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Plugin plugin = (Plugin) o;

        if (!artifactId.equals(plugin.artifactId)) return false;
        return version.equals(plugin.version);
    }

    @Override
    public int hashCode() {
        int result = artifactId.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return artifactId + ":" + version;
    }

    public String getUrl() {
        return "https://plugins.jenkins.io/" + artifactId;
    }

    @Override
    public int compareTo(Plugin o) {
        int cmp = artifactId.compareToIgnoreCase(o.artifactId);
        if (cmp != 0) {
            return cmp;
        }
        return version.compareToIgnoreCase(o.version);
    }
}
