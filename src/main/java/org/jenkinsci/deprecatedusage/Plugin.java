package org.jenkinsci.deprecatedusage;

import java.util.Objects;

public record Plugin(String artifactId, String version) implements Comparable<Plugin> {
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
