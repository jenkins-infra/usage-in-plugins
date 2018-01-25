**Finds and reports usage of**
* **deprecated Jenkins api in plugins** (except api used in jelly and groovy files and in WEB-INF/lib/*.jar)
* **deprecated plugin api in dependent plugins**

[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Reporting/deprecated-usage-in-plugins)](https://ci.jenkins.io/job/Reporting/job/deprecated-usage-in-plugins/)

Current results in summary:
* 1114 plugins
* 560 plugins using a deprecated Jenkins api
* 20 deprecated classes, 170 deprecated methods and 12 deprecated fields are used in plugins
* 28 deprecated and public Jenkins classes, 319 deprecated methods, 58 deprecated fields are not used in the latest published plugins

See details and deprecated usage for each plugin in the [continuous integration](https://ci.jenkins.io/job/Reporting/job/deprecated-usage-in-plugins/lastSuccessfulBuild/artifact/output/).

See also:
* [Jenkins policy for API deprecation](https://issues.jenkins-ci.org/browse/JENKINS-31035)
* The old [Plugin compat tester](https://github.com/jenkinsci/plugin-compat-tester) and its [disabled job](https://ci.jenkins-ci.org/job/plugin-compat-tester/)

To run the tool yourself : Checkout and run with "mvn clean compile exec:java".
Note: it is quite long to download all the plugins the first time (1.8 GB).

[License MIT](../../blob/master/LICENSE.txt)

Author Emeric Vernat
