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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public interface SearchCriteria {
    boolean isLookingForClass(String className);

    //TODO could be reduced to just methodKey

    /**
     * @param methodKey Value is coming from {@link org.jenkinsci.deprecatedusage.DeprecatedApi#getMethodKey(String, String, String)}
     * @param className Class containing the method, used in the methodKey
     * @param methodName Method name, used in the methodKey
     */
    boolean isLookingForMethod(String methodKey, String className, String methodName);

    boolean isLookingForField(String fieldKey, String className, String fieldName);

    boolean shouldAnalyzeClass(String className);

    default SearchCriteria combineWith(SearchCriteria other) {
        List<SearchCriteria> list = new ArrayList<>();

        if (this instanceof CombinedSearchCriteria) {
            list.addAll(((CombinedSearchCriteria) this).searchCriteriaList);
        } else {
            list.add(this);
        }
        if (other instanceof CombinedSearchCriteria) {
            list.addAll(((CombinedSearchCriteria) other).searchCriteriaList);
        } else {
            list.add(other);
        }
        return new CombinedSearchCriteria(list);
    }
}

class CombinedSearchCriteria implements SearchCriteria {
    final List<SearchCriteria> searchCriteriaList;

    public CombinedSearchCriteria(List<SearchCriteria> searchCriteriaList) {
        this.searchCriteriaList = searchCriteriaList;
    }

    public CombinedSearchCriteria(SearchCriteria... searchCriteriaList) {
        this.searchCriteriaList = Arrays.asList(searchCriteriaList);
    }

    @Override
    public boolean isLookingForClass(String className) {
        return searchCriteriaList.stream().anyMatch(sc -> sc.isLookingForClass(className));
    }

    @Override
    public boolean isLookingForMethod(String methodKey, String className, String methodName) {
        return searchCriteriaList.stream().anyMatch(sc -> sc.isLookingForMethod(methodKey, className, methodName));
    }

    @Override
    public boolean isLookingForField(String fieldKey, String className, String methodName) {
        return searchCriteriaList.stream().anyMatch(sc -> sc.isLookingForField(fieldKey, className, methodName));
    }

    @Override
    public boolean shouldAnalyzeClass(String className) {
        return searchCriteriaList.stream().anyMatch(sc -> sc.shouldAnalyzeClass(className));
    }
}
