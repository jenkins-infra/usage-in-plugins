package org.jenkinsci.deprecatedusage;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavadocUtil {

    private static final String JAVADOC_URL = "http://javadoc.jenkins.io/";

    public static String signatureToJenkinsdocLink(String fullSignature) {
        return signatureToJenkinsdocLink(fullSignature, fullSignature);
    }

    public static String signatureToJenkinsdocLink(String fullSignature, String label) {
        String url = signatureToJenkinsdocUrl(fullSignature);

        label = label.replace("<", "&lt;").replace(">", "&gt;");

        if (!fullSignature.contains("jenkins") && !fullSignature.contains("hudson")) {
            return label;
        }

        return "<a href='" + url+ "'>" + label + "</a>";
    }

    public static String signatureToJenkinsdocUrl(String fullSignature) {

        boolean isClass = !fullSignature.contains("#");
        boolean isField = !isClass && !fullSignature.contains("(");

        if (isClass) {
            // transform package and class names, then return
            return JAVADOC_URL + fullSignature.replace("$", ".") + ".html";
        }

        if (isField) {
            return JAVADOC_URL + fullSignature.replace("$", ".").replace("#", ".html#");
        }

        String packageName = "";
        String classMethodArgumentsAndReturn = fullSignature;
        String packageAndClass = fullSignature.substring(0, fullSignature.indexOf("#"));

        int endOfPackage =  packageAndClass.lastIndexOf("/");
        if (endOfPackage > 0) {
            packageName = fullSignature.substring(0, endOfPackage);
            classMethodArgumentsAndReturn = fullSignature.substring(endOfPackage + 1);
        }

        int returnValue = classMethodArgumentsAndReturn.indexOf(")") + 1;
        String classMethodAndArguments = classMethodArgumentsAndReturn.substring(0, returnValue);

        String className = classMethodAndArguments.substring(0, classMethodAndArguments.indexOf("#"));
        className = className.replace("$", ".");
        String methodName = classMethodAndArguments.substring(classMethodAndArguments.indexOf("#") + 1, classMethodAndArguments.indexOf("(")).replace("<init>", className);
        String arguments = classMethodAndArguments.substring(classMethodAndArguments.indexOf("(") + 1, classMethodAndArguments.indexOf(")"));

        List<String> processedArgs = new ArrayList<>();
        if (!arguments.isEmpty()) {
            Scanner scanner = new Scanner(arguments);
            String markerPattern = "[LZBCIV\\[]";
            while (scanner.hasNext(markerPattern)) {
                processedArgs.add(scanParameterToHuman(scanner));
            }
            arguments = StringUtils.join(processedArgs.toArray(), ",%20");
        }

        return JAVADOC_URL + packageName + '/' + className + ".html#" + methodName + "%28" + arguments + "%29";
    }

    private static String scanParameterToHuman(Scanner scanner) {
        String markerPattern = "[LZBCIV\\[]";

        String marker = scanner.next(markerPattern);
        if (marker == null) {
            return "unknown";
        }
        switch (marker) {
            case "[" -> {
                // array
                return scanParameterToHuman(scanner) + "[]";
                // array
            }
            case "L" -> {
                String className = scanner.next("[^;]+;");
                return className.substring(0, className.length() - 1).replace("$", ".").replace("/", ".");
            }
            case "Z" -> {
                return "boolean";
            }
            case "I" -> {
                return "int";
            }
            case "B" -> {
                return "byte";
            }
            case "C" -> {
                return "char";
            }
        }

        return marker;
    }

    private static class Scanner {
        private final String str;
        private int index = 0;
        public Scanner(String str) {
            this.str = str;
        }

        public boolean hasNext(String pattern) {
            return hasNext(Pattern.compile(pattern).matcher(str.substring(index)));
        }

        private boolean hasNext(Matcher matcher) {
            return matcher.lookingAt();
        }

        public String next(String pattern) {
            Matcher matcher = Pattern.compile(pattern).matcher(str.substring(index));
            if (hasNext(matcher)) {
                String ret = matcher.group();
                index += ret.length();
                return ret;
            }
            return null;
        }
    }
}
