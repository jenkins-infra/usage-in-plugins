package org.jenkinsci.deprecatedusage;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavadocUtil {

    private static final String CORE_JAVADOC_URL = "http://javadoc.jenkins.io/";
    // from https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.2
    // and  https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.3
    private static final String MARKER_PATTERN = "[LBCDFIJSZV\\[]";

    public static final JavadocUtil CORE = new JavadocUtil(CORE_JAVADOC_URL, true);
    public static final JavadocUtil PLUGINS = new JavadocUtil(null, false);

    private final String url;
    private final boolean includeLinks;

    private JavadocUtil(String url, boolean includeLinks) {
        this.url = url;
        this.includeLinks = includeLinks;
    }

    public String signatureToJenkinsdocLink(String fullSignature) {
        return signatureToJenkinsdocLink(fullSignature, fullSignature);
    }

    String signatureToJenkinsdocLink(String fullSignature, String label) {
        if (!includeLinks) {
            return label;
        }

        String url = signatureToJenkinsdocUrl(fullSignature);

        label = label.replace("<", "&lt;").replace(">", "&gt;");

        if (!fullSignature.contains("jenkins") && !fullSignature.contains("hudson")) {
            return label;
        }

        return "<a href='" + url+ "'>" + label + "</a>";
    }

    public String signatureToJenkinsdocUrl(String fullSignature) {

        boolean isClass = !fullSignature.contains("#");
        boolean isField = !isClass && !fullSignature.contains("(");

        if (isClass) {
            // transform package and class names, then return
            return CORE_JAVADOC_URL + fullSignature.replace("$", ".") + ".html";
        }

        if (isField) {
            return CORE_JAVADOC_URL + fullSignature.replace("$", ".").replace("#", ".html#");
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
        String lastPartOfClassName = className;
        int startOfLastPartOfClassName = className.lastIndexOf('.');
        if (startOfLastPartOfClassName > 0) {
            lastPartOfClassName = className.substring(startOfLastPartOfClassName + 1);
        }
        String methodName = classMethodAndArguments.substring(classMethodAndArguments.indexOf("#") + 1, classMethodAndArguments.indexOf("(")).replace("<init>", lastPartOfClassName);
        String arguments = classMethodAndArguments.substring(classMethodAndArguments.indexOf("(") + 1, classMethodAndArguments.indexOf(")"));

        List<String> processedArgs = new ArrayList<>();
        if (arguments.length() > 0) {
            Scanner scanner = new Scanner(arguments);
            while (scanner.hasNext(MARKER_PATTERN)) {
                processedArgs.add(scanParameterToHuman(scanner));
            }
            arguments = StringUtils.join(processedArgs.toArray(), "-");
        }

        return CORE_JAVADOC_URL + packageName + '/' + className + ".html#" + methodName + "-" + arguments + "-";
    }

    private static String scanParameterToHuman(Scanner scanner) {

        String marker = scanner.next(MARKER_PATTERN);
        if (marker.equals("[")) {
            // array
            return scanParameterToHuman(scanner) + ":A";
        }

        if (marker.equals("L")) {
            String className = scanner.next("[^;]+;");
            return className.substring(0, className.length() - 1).replace("$", ".").replace("/", ".");
        }

        if (marker.equals("B")) {
            return "byte";
        }

        if (marker.equals("C")) {
            return "char";
        }

        if (marker.equals("D")) {
            return "double";
        }

        if (marker.equals("F")) {
            return "float";
        }

        if (marker.equals("I")) {
            return "int";
        }

        if (marker.equals("J")) {
            return "long";
        }

        if (marker.equals("S")) {
            return "short";
        }

        if (marker.equals("Z")) {
            return "boolean";
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
