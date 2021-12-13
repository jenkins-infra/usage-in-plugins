# Tool to scan for API Usage in Jenkins plugins

This tools offers a facility to scan Jenkins plugins for a given API usage.
It will automatically download all existing Jenkins plugins, and analyze them using the provided criteria.

NOTE: it will **NOT** find usages in Jelly and Groovy files. Finding usage in `WEB-INF/lib/*.jar` is optional (`-p`).

[![Build Status](https://ci.jenkins.io/job/Infra/job/deprecated-usage-in-plugins/job/master/badge/icon)](https://ci.jenkins.io/job/Infra/job/deprecated-usage-in-plugins/job/master/)

See details and deprecated usages for each plugin in the [continuous integration](https://ci.jenkins.io/job/Infra/job/usage-in-plugins/job/master/lastSuccessfulBuild/artifact/output/).


## Usage

To run the tool yourself, see [USAGE](USAGE.adoc)

## See also:

* [Jenkins policy for API deprecation](https://issues.jenkins-ci.org/browse/JENKINS-31035)
* [Plugin compat tester](https://github.com/jenkinsci/plugin-compat-tester) and its [job](https://ci.jenkins.io/job/jenkinsci-libraries/job/plugin-compat-tester/job/master/)

### Historical note

This tool was originally designed to look exclusively for `@Deprecated` code usages.
In early 2019, this has been extended to allow looking for any class usage.

### References

Creator: Emeric Vernat

## LICENSE

[License MIT](../../blob/master/LICENSE.txt)
