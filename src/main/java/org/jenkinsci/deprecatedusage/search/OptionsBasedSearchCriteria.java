/*
 * The MIT License
 *
 * Copyright (c) 2021, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.deprecatedusage.search;

import org.jenkinsci.deprecatedusage.DeprecatedUsage;
import org.jenkinsci.deprecatedusage.Options;

import java.util.Set;

public class OptionsBasedSearchCriteria implements SearchCriteria {
    public OptionsBasedSearchCriteria() {
    }

    @Override
    public boolean isLookingForClass(String className) {
        if (Options.get().additionalClassesFile != null) {
            return Options.getAdditionalClasses().contains(className);
        }
        return false;
    }

    @Override
    public boolean isLookingForMethod(String methodKey, String className, String methodName) {
        if (Options.get().additionalMethodsFile != null) {
            Set<String> classToMethods = Options.getAdditionalMethodNames().get(className);
            return classToMethods != null && classToMethods.contains(methodName);
        }
        return false;
    }

    @Override
    public boolean isLookingForField(String fieldKey, String className, String fieldName) {
        if (Options.get().additionalFieldsFile != null) {
            Set<String> classToFields = Options.getAdditionalFields().get(className);
            return classToFields != null && classToFields.contains(fieldName);
        }
        return false;
    }

    @Override
    public boolean shouldAnalyzeClass(String className) {
        // if an additionalClasses file is specified, and this matches, we ignore Options' includeJavaCoreClasses or onlyIncludeJenkinsClasses
        // values, given the least surprise is most likely that if the user explicitly passed a file, s/he does want it to be analyzed
        // even if coming from java.*, javax.*, or not from Jenkins core classes itself
        Options options = Options.get();
        if (options.additionalClassesFile != null &&
                Options.getAdditionalClasses().stream().anyMatch(className::startsWith)) {
            return true;
        }
        if (options.additionalMethodsFile != null &&
                Options.getAdditionalMethodNames().keySet().stream().anyMatch(className::startsWith)) {
            return true;
        }
        if (options.additionalFieldsFile != null &&
                Options.getAdditionalFields().keySet().stream().anyMatch(className::startsWith)) {
            return true;
        }

        if (options.onlyIncludeSpecified) {
            return false;
        }

        // Calls to java and javax are ignored by default if not explicitly requested
        if (DeprecatedUsage.isJavaClass(className)) {
            return options.includeJavaCoreClasses;
        }

        if (!className.contains("jenkins") && !className.contains("hudson") && !className.contains("org/kohsuke")) {
            return options.onlyIncludeJenkinsClasses;
        }

        return true;
    }
}
