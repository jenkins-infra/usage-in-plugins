package org.jenkinsci.deprecatedusage;

import java.io.File;
import java.util.List;

public class JenkinsCoreAnalysis implements Analysis {
    @Override
    public String getAnalyzedFileName() {
        return "Jenkins";
    }

    @Override
    public JenkinsFile getAnalyzedFile(UpdateCenter updateCenter) {
        return updateCenter.getCore();
    }

    @Override
    public String getDependentFilesName() {
        return "plugins";
    }

    @Override
    public List<JenkinsFile> getDependentFiles(UpdateCenter updateCenter) {
        return updateCenter.getPlugins();
    }

    @Override
    public File getOutputDirectory(String baseDir) {
        return new File(baseDir);
    }

    @Override
    public boolean areSignatureFiltered() {
        return true;
    }

    @Override
    public boolean skipIfNoDeprecatedApis() {
        return false;
    }

    @Override
    public JavadocUtil getJavadocUtil() {
        return JavadocUtil.CORE;
    }
}
