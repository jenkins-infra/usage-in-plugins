package org.jenkinsci.deprecatedusage;

import org.junit.jupiter.api.Test;

import static org.jenkinsci.deprecatedusage.JavadocUtil.signatureToJenkinsdocUrl;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JavadocUtilTest {

    @Test
    void testLinking() {
        assertEquals("http://javadoc.jenkins.io/hudson/util/RunList.html#size%28%29",
                signatureToJenkinsdocUrl("hudson/util/RunList#size()I"));

        assertEquals("http://javadoc.jenkins.io/hudson/util/ChartUtil.html#generateGraph%28org.kohsuke.stapler.StaplerRequest,%20org.kohsuke.stapler.StaplerResponse,%20org.jfree.chart.JFreeChart,%20int,%20int%29",
                signatureToJenkinsdocUrl("hudson/util/ChartUtil#generateGraph(Lorg/kohsuke/stapler/StaplerRequest;Lorg/kohsuke/stapler/StaplerResponse;Lorg/jfree/chart/JFreeChart;II)V"));

        assertEquals("http://javadoc.jenkins.io/hudson/util/IOUtils.html#write%28byte[],%20java.io.OutputStream%29",
                signatureToJenkinsdocUrl("hudson/util/IOUtils#write([BLjava/io/OutputStream;)V"));

        assertEquals("http://javadoc.jenkins.io/hudson/Launcher.html#launch%28java.lang.String[],%20java.lang.String[],%20java.io.InputStream,%20java.io.OutputStream,%20hudson.FilePath%29",
                signatureToJenkinsdocUrl("hudson/Launcher#launch([Ljava/lang/String;[Ljava/lang/String;Ljava/io/InputStream;Ljava/io/OutputStream;Lhudson/FilePath;)Lhudson/Proc;"));

        assertEquals("http://javadoc.jenkins.io/hudson/Launcher.html#launch%28java.lang.String,%20java.util.Map,%20java.io.OutputStream,%20hudson.FilePath%29",
                signatureToJenkinsdocUrl("hudson/Launcher#launch(Ljava/lang/String;Ljava/util/Map;Ljava/io/OutputStream;Lhudson/FilePath;)Lhudson/Proc;"));

        assertEquals("http://javadoc.jenkins.io/hudson/tools/ToolInstallation.html#ToolInstallation%28java.lang.String,%20java.lang.String%29",
                signatureToJenkinsdocUrl("hudson/tools/ToolInstallation#<init>(Ljava/lang/String;Ljava/lang/String;)V"));

        assertEquals("http://javadoc.jenkins.io/hudson/util/ChartUtil.NumberOnlyBuildLabel.html#ChartUtil.NumberOnlyBuildLabel%28hudson.model.AbstractBuild%29",
                signatureToJenkinsdocUrl("hudson/util/ChartUtil$NumberOnlyBuildLabel#<init>(Lhudson/model/AbstractBuild;)V"));

        assertEquals("http://javadoc.jenkins.io/hudson/slaves/DumbSlave.html#DumbSlave%28java.lang.String,%20java.lang.String,%20java.lang.String,%20java.lang.String,%20hudson.model.Node.Mode,%20java.lang.String,%20hudson.slaves.ComputerLauncher,%20hudson.slaves.RetentionStrategy,%20java.util.List%29",
                signatureToJenkinsdocUrl("hudson/slaves/DumbSlave#<init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lhudson/model/Node$Mode;Ljava/lang/String;Lhudson/slaves/ComputerLauncher;Lhudson/slaves/RetentionStrategy;Ljava/util/List;)V"));

        assertEquals("http://javadoc.jenkins.io/hudson/model/Build.RunnerImpl.html",
                signatureToJenkinsdocUrl("hudson/model/Build$RunnerImpl"));

        assertEquals("http://javadoc.jenkins.io/hudson/util/ChartUtil.NumberOnlyBuildLabel.html#build",
                signatureToJenkinsdocUrl("hudson/util/ChartUtil$NumberOnlyBuildLabel#build"));
    }
}
