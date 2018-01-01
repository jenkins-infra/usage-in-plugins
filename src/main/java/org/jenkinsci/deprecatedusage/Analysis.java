package org.jenkinsci.deprecatedusage;

import java.io.File;
import java.util.List;

public interface Analysis {
    String getAnalyzedFileName();

    JenkinsFile getAnalyzedFile(UpdateCenter updateCenter);

    String getDependentFilesName();

    List<JenkinsFile> getDependentFiles(UpdateCenter updateCenter);

    File getOutputDirectory(String baseDir);

    boolean areSignatureFiltered();
}

