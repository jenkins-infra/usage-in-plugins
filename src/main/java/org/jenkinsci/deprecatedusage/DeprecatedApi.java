package org.jenkinsci.deprecatedusage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.AnnotationVisitor;
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

    private final Map<String, String> classes = new LinkedHashMap<>();
    private final Map<String, String> methods = new LinkedHashMap<>();
    private final Map<String, String> fields = new LinkedHashMap<>();
    private final ClassVisitor classVisitor = new CalledClassVisitor();

    private String currentComponent;

    public static String getMethodKey(String className, String name, String desc) {
        return className + SEPARATOR + name + desc;
    }

    public static String getFieldKey(String className, String name, String desc) {
        return className + SEPARATOR + name; // + SEPARATOR + desc;
        // desc (ie type) of a field is not necessary to identify the field.
        // it is ignored since it would only clutter reports
    }

    public void analyze(File coreFile) throws IOException {
        currentComponent = coreFile.getName().substring(0, coreFile.getName().indexOf("."));
        final WarReader warReader = new WarReader(coreFile, false);
        try {
            String fileName = warReader.nextClass();
            while (fileName != null) {
                analyze(warReader.getInputStream());
                fileName = warReader.nextClass();
            }
        } catch(Exception e) {
            System.out.println("skipping " + coreFile);
        } finally {
            warReader.close();
        }
        for (String ignored : IGNORED_DEPRECATED_CLASSES) {
            classes.remove(ignored);
        }
    }

    private void analyze(InputStream input) throws IOException {
        final ClassReader classReader = new ClassReader(input);
        classReader.accept(classVisitor,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    public Map<String, String> getClasses() {
        return  classes;
    }

    public Map<String, String> getMethods() {
        return methods;
    }

    public Map<String, String> getFields() {
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

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            // log(name + " extends " + superName + " {");
            if (isPublic(access)) {
                currentClass = name;
            } else {
                currentClass = null;
            }
        }

        private boolean isRestrictedAnnotation(String desc) {
            return desc != null && desc.contains("Restricted");
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (currentClass != null) {
                if (isRestrictedAnnotation(desc)) {
                    classes.put(currentClass, currentComponent);
                }
            }
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, final String name, String desc, String signature,
                String[] exceptions) {
            if (currentClass != null && isPublic(access)) {
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        if (isRestrictedAnnotation(desc)) {
                            methods.put(getMethodKey(currentClass, name, desc), currentComponent);
                        }
                        return null;
                    }
                };
            }
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, final String name, String desc, String signature,
                Object value) {
            if (currentClass != null && isPublic(access)) {
                return new FieldVisitor(Opcodes.ASM5) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        if (isRestrictedAnnotation(desc)) {
                            fields.put(getFieldKey(currentClass, name, desc), currentComponent);
                        }
                        return null;
                    }
                };
            }
            return null;
        }
    }
}
