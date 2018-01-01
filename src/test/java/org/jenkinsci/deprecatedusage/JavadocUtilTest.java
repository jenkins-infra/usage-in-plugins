package org.jenkinsci.deprecatedusage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.runners.Parameterized.*;

@RunWith(Parameterized.class)
public class JavadocUtilTest {

    @Parameters(name = "testLinking({0})")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {
                "hudson/util/RunList#size()I",
                "http://javadoc.jenkins.io/hudson/util/RunList.html#size%28%29"
            },
            {
                "hudson/util/ChartUtil#generateGraph(Lorg/kohsuke/stapler/StaplerRequest;Lorg/kohsuke/stapler/StaplerResponse;Lorg/jfree/chart/JFreeChart;II)V",
                "http://javadoc.jenkins.io/hudson/util/ChartUtil.html#generateGraph%28org.kohsuke.stapler.StaplerRequest,%20org.kohsuke.stapler.StaplerResponse,%20org.jfree.chart.JFreeChart,%20int,%20int%29"
            },
            {
                "hudson/util/IOUtils#write([BLjava/io/OutputStream;)V",
                "http://javadoc.jenkins.io/hudson/util/IOUtils.html#write%28byte[],%20java.io.OutputStream%29"
            },
            {
                "hudson/Launcher#launch([Ljava/lang/String;[Ljava/lang/String;Ljava/io/InputStream;Ljava/io/OutputStream;Lhudson/FilePath;)Lhudson/Proc;",
                "http://javadoc.jenkins.io/hudson/Launcher.html#launch%28java.lang.String[],%20java.lang.String[],%20java.io.InputStream,%20java.io.OutputStream,%20hudson.FilePath%29"
            },
            {
                "hudson/Launcher#launch(Ljava/lang/String;Ljava/util/Map;Ljava/io/OutputStream;Lhudson/FilePath;)Lhudson/Proc;",
                "http://javadoc.jenkins.io/hudson/Launcher.html#launch%28java.lang.String,%20java.util.Map,%20java.io.OutputStream,%20hudson.FilePath%29"
            },
            {
                "hudson/tools/ToolInstallation#<init>(Ljava/lang/String;Ljava/lang/String;)V",
                "http://javadoc.jenkins.io/hudson/tools/ToolInstallation.html#ToolInstallation%28java.lang.String,%20java.lang.String%29"
            },
            {
                "hudson/util/ChartUtil$NumberOnlyBuildLabel#<init>(Lhudson/model/AbstractBuild;)V",
                "http://javadoc.jenkins.io/hudson/util/ChartUtil.NumberOnlyBuildLabel.html#ChartUtil.NumberOnlyBuildLabel%28hudson.model.AbstractBuild%29"
            },
            {
                "hudson/slaves/DumbSlave#<init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lhudson/model/Node$Mode;Ljava/lang/String;Lhudson/slaves/ComputerLauncher;Lhudson/slaves/RetentionStrategy;Ljava/util/List;)V",
                "http://javadoc.jenkins.io/hudson/slaves/DumbSlave.html#DumbSlave%28java.lang.String,%20java.lang.String,%20java.lang.String,%20java.lang.String,%20hudson.model.Node.Mode,%20java.lang.String,%20hudson.slaves.ComputerLauncher,%20hudson.slaves.RetentionStrategy,%20java.util.List%29"
            },
            {
                "hudson/model/Build$RunnerImpl",
                "http://javadoc.jenkins.io/hudson/model/Build.RunnerImpl.html"
            },
            {
                "hudson/util/ChartUtil$NumberOnlyBuildLabel#build",
                "http://javadoc.jenkins.io/hudson/util/ChartUtil.NumberOnlyBuildLabel.html#build"
            },
            {
                "hudson/node_monitors/AbstractNodeMonitorDescriptor#<init>(J)V",
                "http://javadoc.jenkins.io/hudson/node_monitors/AbstractNodeMonitorDescriptor.html#AbstractNodeMonitorDescriptor%28long%29"
            },
            {
                "hudson/model/MultiStageTimeSeries#<init>(FF)V",
                "http://javadoc.jenkins.io/hudson/model/MultiStageTimeSeries.html#MultiStageTimeSeries%28float,%20float%29"
            },
            {
                // FAKED ONE, no public method in jenkins with a double found
                "hudson/node_monitors/AbstractNodeMonitorDescriptor#<init>(D)V",
                "http://javadoc.jenkins.io/hudson/node_monitors/AbstractNodeMonitorDescriptor.html#AbstractNodeMonitorDescriptor%28double%29"
            },
            {
                // FAKED ONE, no public method in jenkins with a short found
                "hudson/node_monitors/AbstractNodeMonitorDescriptor#<init>(S)V",
                "http://javadoc.jenkins.io/hudson/node_monitors/AbstractNodeMonitorDescriptor.html#AbstractNodeMonitorDescriptor%28short%29"
            }
        });
    }

    @Parameter()
    public String signature;

    @Parameter(1)
    public String expectedLink;

    @Test
    public void testLinking() {
        assertEquals(expectedLink, new JavadocUtil().signatureToJenkinsdocUrl(signature));
        System.out.println( new JavadocUtil().signatureToJenkinsdocUrl(signature));
    }
}
