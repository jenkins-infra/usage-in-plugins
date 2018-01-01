package org.jenkinsci.deprecatedusage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

public class UpdateCenter {
    private final URL updateCenterUrl;
    private final JenkinsFile core;
    private final List<JenkinsFile> plugins = new ArrayList<>();

    public UpdateCenter(URL updateCenterUrl)
            throws IOException, ParserConfigurationException, SAXException {
        super();
        this.updateCenterUrl = updateCenterUrl;
        final String string = getUpdateCenterJson();

        final JSONObject jsonRoot = new JSONObject(string);
        final JSONObject jsonCore = jsonRoot.getJSONObject("core");
        core = parseCore(jsonCore);

        final JSONObject jsonPlugins = jsonRoot.getJSONObject("plugins");
        for (final Object pluginId : jsonPlugins.keySet()) {
            final JSONObject jsonPlugin = jsonPlugins.getJSONObject(pluginId.toString());
            final JenkinsFile plugin = parse(jsonPlugin);
            plugins.add(plugin);
        }
        final Comparator<JenkinsFile> comparator = (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName());
        plugins.sort(comparator);
    }

    private String getUpdateCenterJson() throws IOException, MalformedURLException {
        final byte[] updateCenterData = new HttpGet(updateCenterUrl).read();
        final String string = new String(updateCenterData, StandardCharsets.UTF_8)
                .replace("updateCenter.post(", "");
        return string;
    }

    private JenkinsFile parseCore(JSONObject jsonObject) throws MalformedURLException, JSONException {
        JenkinsFile core = parse(jsonObject);
        core.setFile(new File(System.getProperty("coreFileOverride", core.getFile().toString())));
        return core;
    }

    private JenkinsFile parse(JSONObject jsonObject) throws MalformedURLException, JSONException {
        final String wiki = jsonObject.optString("wiki");
        final HashSet<String> dependencies = new HashSet<>();
        JSONArray jsonDependencies = jsonObject.optJSONArray("dependencies");
        if (jsonDependencies != null) {
            for (int i = 0; i < jsonDependencies.length(); i++) {
                dependencies.add(jsonDependencies.getJSONObject(i).getString("name"));
            }
        }
        return new JenkinsFile(jsonObject.getString("name"), jsonObject.getString("version"),
                jsonObject.getString("url"), wiki, dependencies);
    }

    public void download() throws Exception {
        // download in parallel
        core.startDownloadIfNotExists();
        for (final JenkinsFile plugin : plugins) {
            plugin.startDownloadIfNotExists();
        }
        // wait end of downloads
        core.waitDownload();
        for (final JenkinsFile plugin : new ArrayList<>(plugins)) {
            try {
                plugin.waitDownload();
            } catch (final FileNotFoundException e) {
                System.err.println(e.toString());
                plugins.remove(plugin);
            }
        }
    }

    public JenkinsFile getCore() {
        return core;
    }

    public List<JenkinsFile> getPlugins() {
        return plugins;
    }
}
