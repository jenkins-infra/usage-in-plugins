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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class OptionsBasedSearchCriteria implements SearchCriteria {
    
    private Predicate<String> directClassChecker;
    private Predicate<String> methodClassChecker;
    private Predicate<String> fieldClassChecker;
    
    public OptionsBasedSearchCriteria() {
        if (Options.get().additionalClassesFile != null) {
            this.directClassChecker = convertToChecker(Options.getAdditionalClasses());
        }
        if (Options.get().additionalMethodsFile != null) {
            this.methodClassChecker = convertToChecker(Options.getAdditionalMethodNames().keySet());
        }
        if (Options.get().additionalFieldsFile != null) {
            this.fieldClassChecker = convertToChecker(Options.getAdditionalFields().keySet());
        }
    }

    @Override
    public boolean isLookingForClass(String className) {
        if (directClassChecker != null) {
            return directClassChecker.test(className);
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
        // if an additionalClasses file is specified, and this matches, 
        // we ignore Options' includeJavaCoreClasses or onlyIncludeJenkinsClasses values, 
        // given the least surprise is most likely that if the user explicitly passed a file, 
        // they does want it to be analyzed even if coming from java.*, javax.*, or not from Jenkins core classes itself
        if (directClassChecker != null && directClassChecker.test(className)) {
            return true;
        }
        if (methodClassChecker != null && methodClassChecker.test(className)) {
            return true;
        }
        if (fieldClassChecker != null && fieldClassChecker.test(className)) {
            return true;
        }

        Options options = Options.get();
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
    
    private Predicate<String> convertToChecker(Collection<String> classNames) {
        Set<String> exactMatch = new HashSet<>();
        
        // Could be optimized with some tree structure
        List<String> startsWith = new ArrayList<>();
        List<String> endsWith = new ArrayList<>();
        List<String> contains = new ArrayList<>();

        classNames.forEach(cn -> {
            // careful, the star position is reversed compared to the behavior
            // we want the string that startsWith Xxx, so the pattern will be Xxx* (star is at the end)
            boolean isStartingWith = cn.endsWith("*");
            boolean isEndingWith = cn.startsWith("*");
            if (isStartingWith) {
                if (isEndingWith) {
                    contains.add(cn.substring(1, cn.length() - 2));
                } else {
                    startsWith.add(cn.substring(0, cn.length() - 1));
                }
            } else {
                if (isEndingWith) {
                    endsWith.add(cn.substring(1));
                } else {
                    exactMatch.add(cn);
                }
            }
        });

        List<Predicate<String>> predicates = new ArrayList<>();

        // Filling the predicates when their corresponding list is not empty
        if (!exactMatch.isEmpty()) {
            predicates.add(exactMatch::contains);
        }
        if (!startsWith.isEmpty()) {
            predicates.add(targetClassName -> startsWith.stream().anyMatch(targetClassName::startsWith));
        }
        if (!endsWith.isEmpty()) {
            predicates.add(targetClassName -> endsWith.stream().anyMatch(targetClassName::endsWith));
        }
        if (!contains.isEmpty()) {
            predicates.add(targetClassName -> contains.stream().anyMatch(targetClassName::contains));
        }

        // Combining the predicates together
        if (predicates.isEmpty()) {
            return s -> false;
        } else if (predicates.size() == 1) {
            return predicates.get(0);
        } else {
            Predicate<String> curr, first = curr = predicates.get(0);
            for (int i = 1; i < predicates.size(); i++) {
                curr = curr.or(predicates.get(i));
            }
            return first;
        }
    }
}
