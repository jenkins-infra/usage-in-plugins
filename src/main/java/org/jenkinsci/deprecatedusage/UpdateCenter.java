package org.jenkinsci.deprecatedusage;

import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class UpdateCenter {
    private final JenkinsFile core;
    private final List<JenkinsFile> plugins = new ArrayList<>();

    public UpdateCenter(JSONObject metadata) {
        JSONObject jsonCore = metadata.getJSONObject("core");
        core = parse(jsonCore);
        JSONObject jsonPlugins = metadata.getJSONObject("plugins");
        for (Object pluginId : jsonPlugins.keySet()) {
            JSONObject jsonPlugin = jsonPlugins.getJSONObject(pluginId.toString());
            JenkinsFile plugin = parse(jsonPlugin);
            plugins.add(plugin);
        }
    }

    private static JenkinsFile parse(JSONObject jsonObject) throws JSONException {
        final String wiki;
        if (jsonObject.has("wiki")) {
            wiki = jsonObject.getString("wiki");
        } else {
            wiki = null;
        }
        final Base64.Decoder decoder = Base64.getDecoder();
        final Checksum checksum;
        if (jsonObject.has("sha256")) {
            byte[] digest = decoder.decode(jsonObject.getString("sha256"));
            checksum = Checksum.fromDigest("SHA-256", digest, DigestUtils::sha256);
        } else if (jsonObject.has("sha1")) {
            byte[] digest = decoder.decode(jsonObject.getString("sha1"));
            checksum = Checksum.fromDigest("SHA-1", digest, DigestUtils::sha1);
        } else {
            checksum = null;
        }
        return new JenkinsFile(jsonObject.getString("name"), jsonObject.getString("version"),
                jsonObject.getString("url"), wiki, checksum);
    }

    public JenkinsFile getCore() {
        return core;
    }

    public List<JenkinsFile> getPlugins() {
        return plugins;
    }
}
