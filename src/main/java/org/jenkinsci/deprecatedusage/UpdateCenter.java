package org.jenkinsci.deprecatedusage;

import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class UpdateCenter {
    private final JenkinsFile core;
    private final List<JenkinsFile> plugins = new ArrayList<>();

    public UpdateCenter(JSONObject metadata) {
        JSONObject jsonCore = metadata.optJSONObject("core");
        core = parse(jsonCore);
        JSONObject jsonPlugins = metadata.getJSONObject("plugins");
        for (Object pluginId : jsonPlugins.keySet()) {
            JSONObject jsonPlugin = jsonPlugins.getJSONObject(pluginId.toString());
            JenkinsFile plugin = parse(jsonPlugin);
            plugins.add(plugin);
        }
    }

    private static JenkinsFile parse(JSONObject jsonObject) throws JSONException {
        if (jsonObject == null) {
            return null;
        }
        final String wiki;
        if (jsonObject.has("wiki")) {
            wiki = jsonObject.getString("wiki");
        } else {
            wiki = null;
        }
        final Base64.Decoder decoder = Base64.getDecoder();
        final MessageDigest messageDigest;
        final byte[] digest;
        if (jsonObject.has("sha256")) {
            digest = decoder.decode(jsonObject.getString("sha256"));
            messageDigest = DigestUtils.getSha256Digest();
        } else if (jsonObject.has("sha1")) {
            digest = decoder.decode(jsonObject.getString("sha1"));
            messageDigest = DigestUtils.getSha1Digest();
        } else {
            messageDigest = null;
            digest = null;
        }
        return new JenkinsFile(jsonObject.getString("name"), jsonObject.getString("version"),
                jsonObject.getString("url"), wiki, messageDigest, digest);
    }

    public JenkinsFile getCore() {
        return core;
    }

    public List<JenkinsFile> getPlugins() {
        return plugins;
    }
}
