package org.jenkinsci.deprecatedusage;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class DeprecatedApi {
    // some plugins such as job-dsl has following code without using deprecated :
    // for (Cloud cloud : Jenkins.getInstance().clouds) { }
    // where the type of jenkins.clouds is of type hudson.model.Hudson.CloudList and deprecated
    // https://github.com/jenkinsci/job-dsl-plugin/blob/job-dsl-1.40/job-dsl-plugin/src/main/groovy/javaposse/jobdsl/plugin/JenkinsJobManagement.java#L359
    // but code is compiled using deprecated as :
    // for (Iterator<Cloud> iter = Jenkins.getInstance().clouds.iterator(); iter.hasNext(); ) {
    // Cloud cloud = iter.next(); }
    // so deprecation of Hudson$CloudList is ignored
    public static final Set<String> IGNORED_DEPRECATED_CLASSES = new HashSet<>(
            Arrays.asList("hudson/model/Hudson$CloudList"));

    private static final char SEPARATOR = '#';

    private final Set<String> classes = new LinkedHashSet<>();

    public static String getMethodKey(String className, String name, String desc) {
        return className + SEPARATOR + name + desc;
    }

    public static String getFieldKey(String className, String name, String desc) {
        return className + SEPARATOR + name; // + SEPARATOR + desc;
        // desc (ie type) of a field is not necessary to identify the field.
        // it is ignored since it would only clutter reports
    }

    public Set<String> getClasses() {
        return classes;
    }

    public boolean isClassFromAcegi(String className) {
        if (isFromAcegi(className)) {
            classes.add(className);
            return true;
        }
        return false;
    }

    public static boolean isFromAcegi(String classFullName) {
        String pack = getPackage(classFullName);
        if (pack.contains("acegisecurity")) {
            return true;
        }
        return false;
    }

    private static String getPackage(String classFullName) {
        int index = classFullName.lastIndexOf('/');
        if (index == -1) {
            return classFullName;
        }
        String packag = classFullName.substring(0, index);
        return packag;
    }

    // azure-ad has a method returning GrantedAuthority[]
    public static String deArrayise(String classFullName) {
        while (classFullName.startsWith("[L")) {
            classFullName = classFullName.substring("[L".length(), classFullName.length() - ";".length());
        }
        return classFullName;
    }
}
