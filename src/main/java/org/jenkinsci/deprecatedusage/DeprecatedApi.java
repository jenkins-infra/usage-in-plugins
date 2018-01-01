package org.jenkinsci.deprecatedusage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

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
    private final Set<String> methods = new LinkedHashSet<>();
    private final Set<String> fields = new LinkedHashSet<>();
    private final ClassVisitor classVisitor = new CalledClassVisitor();

    public static String getMethodKey(String className, String name, String desc) {
        return className + SEPARATOR + name + desc;
    }

    public static String getFieldKey(String className, String name, String desc) {
        return className + SEPARATOR + name; // + SEPARATOR + desc;
        // desc (ie type) of a field is not necessary to identify the field.
        // it is ignored since it would only clutter reports
    }

    public void analyze(File coreFile) throws IOException {
        final WarReader warReader = new WarReader(coreFile, false);
        try {
            String fileName = warReader.nextClass();
            while (fileName != null) {
                analyze(warReader.getInputStream());
                fileName = warReader.nextClass();
            }
        } finally {
            warReader.close();
        }
        classes.removeAll(IGNORED_DEPRECATED_CLASSES);
    }

    private void analyze(InputStream input) throws IOException {
        final ClassReader classReader = new ClassReader(input);
        classReader.accept(classVisitor,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    public boolean hasDeprecatedApis() {
        return !classes.isEmpty() || !methods.isEmpty() || !fields.isEmpty();
    }

    public Set<String> getClasses() {
        return  classes;
    }

    public Set<String> getMethods() {
        return methods;
    }

    public Set<String> getFields() {
        return fields;
    }

    /**
     * Implements ASM ClassVisitor.
     */
    private class CalledClassVisitor extends ClassVisitor {
        private static final int OPCODE_PUBLIC = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED;
        private static final int OPCODE_DEPRECATED = Opcodes.ACC_DEPRECATED;

        private String currentClass;

        CalledClassVisitor() {
            super(Opcodes.ASM5);
        }

        private boolean isPublic(int asmAccess) {
            return (asmAccess & OPCODE_PUBLIC) != 0;
        }

        private boolean isDeprecated(int asmAccess) {
            return (asmAccess & OPCODE_DEPRECATED) != 0;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            // log(name + " extends " + superName + " {");
            if (isPublic(access)) {
                currentClass = name;
                if (isDeprecated(access)) {
                    classes.add(name);
                }
            } else {
                currentClass = null;
            }
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                String[] exceptions) {
            if (currentClass != null && isDeprecated(access) && isPublic(access)) {
                methods.add(getMethodKey(currentClass, name, desc));
            }
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature,
                Object value) {
            if (currentClass != null && isDeprecated(access) && isPublic(access)) {
                fields.add(getFieldKey(currentClass, name, desc));
            }
            return null;
        }
    }
}
