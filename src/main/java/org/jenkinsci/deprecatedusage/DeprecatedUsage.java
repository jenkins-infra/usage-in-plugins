package org.jenkinsci.deprecatedusage;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class DeprecatedUsage {
    // python-wrapper has wrappers for all extension points and descriptors,
    // they are just wrappers and not real usage
    public static final Set<String> IGNORED_PLUGINS = new HashSet<>(
            Arrays.asList("python-wrapper.hpi"));

    private final Plugin plugin;
    private final DeprecatedApi deprecatedApi;

    private final Set<String> classes = new LinkedHashSet<>();
    private final Set<String> methods = new LinkedHashSet<>();
    private final Set<String> fields = new LinkedHashSet<>();
    private final Map<String, List<String>> acegiToClasses = new LinkedHashMap<>();
    private final Map<String, List<String>> acegiToMethods = new LinkedHashMap<>();
    private final Map<String, List<String>> acegiToFields = new LinkedHashMap<>();

//        private final ClassVisitor indexerClassVisitor = new IndexerClassVisitor();
//        private final ClassVisitor classVisitor = new CallersClassVisitor();
    private final ClassVisitor packageVisitor = new PackageClassVisitor();
    private final Map<String, List<String>> superClassAndInterfacesByClass = new HashMap<>();

    public DeprecatedUsage(String pluginName, String pluginVersion, DeprecatedApi deprecatedApi) {
        super();
        this.plugin = new Plugin(pluginName, pluginVersion);
        this.deprecatedApi = deprecatedApi;
    }

    public void analyze(File pluginFile) throws IOException {
        if (IGNORED_PLUGINS.contains(pluginFile.getName())) {
            return;
        }
//                analyzeWithClassVisitor(pluginFile, indexerClassVisitor);
//                analyzeWithClassVisitor(pluginFile, classVisitor);
        analyzeWithClassVisitor(pluginFile, packageVisitor);
    }

    public void analyzeWithClassVisitor(File pluginFile, ClassVisitor aClassVisitor)
            throws IOException {
        // recent plugins package their classes as a jar file with the same name as the war file in
        // WEB-INF/lib/ while older plugins were packaging their classes in WEB-INF/classes/
        final WarReader warReader = new WarReader(pluginFile, true);
        try {
            String fileName = warReader.nextClass();
            while (fileName != null) {
                analyze(warReader.getInputStream(), aClassVisitor);
                fileName = warReader.nextClass();
            }
        } finally {
            warReader.close();
        }
    }

    private void analyze(InputStream input, ClassVisitor aClassVisitor) throws IOException {
        final ClassReader classReader = new ClassReader(input);
        classReader.accept(aClassVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public Set<String> getClasses() {
        return new TreeSet<>(classes);
    }
    

    public Set<String> getMethods() {
        return new TreeSet<>(methods);
    }

    public Set<String> getFields() {
        return new TreeSet<>(fields);
    }
    public Map<String, List<String>> getAcegiToClasses() {
        return new HashMap<>(this.acegiToClasses);
    }
    public Map<String, List<String>> getAcegiToMethods() {
        return new HashMap<>(this.acegiToMethods);
    }
    public Map<String, List<String>> getAcegiToFields() {
        return new HashMap<>(this.acegiToFields);
    }

    public boolean hasDeprecatedUsage() {
        return !classes.isEmpty() || !methods.isEmpty() || !fields.isEmpty();
    }

    void classCalled(String className, String type, String ownerClass) {
        if (ownerClass == null) {
            return;
        }
        className = DeprecatedApi.deArrayise(className);
        if (deprecatedApi.isClassFromAcegi(className)) {
            classes.add(ownerClass);
            acegiToClasses.computeIfAbsent(className, s -> new ArrayList<>()).add(ownerClass);
        }
    }

    void methodCalled(String methodClassName, String type, String ownerClassName, String name, String desc) {
        methodClassName = DeprecatedApi.deArrayise(methodClassName);
        if (deprecatedApi.isClassFromAcegi(methodClassName)) {
            final String method = DeprecatedApi.getMethodKey(ownerClassName, name, desc);
            methods.add(method);
            acegiToMethods.computeIfAbsent(methodClassName, s -> new ArrayList<>()).add(method);
        }
    }

    void fieldCalled(String fieldClassName, String type, String ownerClassName, String name, String desc) {
        fieldClassName = DeprecatedApi.deArrayise(fieldClassName);
        if (deprecatedApi.isClassFromAcegi(fieldClassName)) {
            final String field = DeprecatedApi.getFieldKey(ownerClassName, name, desc);
            fields.add(field);
            acegiToFields.computeIfAbsent(fieldClassName, s -> new ArrayList<>()).add(field);
        }
    }

    private static boolean isJavaClass(String asmClassName) {
        // if starts with java/ or javax/, then it's a class of core java
        return asmClassName.startsWith("java/") || asmClassName.startsWith("javax/");
    }

    /**
     * Implements ASM ClassVisitor.
     */
    private class IndexerClassVisitor extends ClassVisitor {
        IndexerClassVisitor() {
            super(Opcodes.ASM8);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                          String[] interfaces) {
            // log(name + " extends " + superName + " {");
            final List<String> superClassAndInterfaces = new ArrayList<>();
            if (!isJavaClass(superName)) {
                superClassAndInterfaces.add(superName);
            }
            if (interfaces != null) {
                for (final String anInterface : interfaces) {
                    if (!isJavaClass(anInterface)) {
                        superClassAndInterfaces.add(anInterface);
                    }
                }
            }
            if (!superClassAndInterfaces.isEmpty()) {
                superClassAndInterfacesByClass.put(name, superClassAndInterfaces);
            }
        }
    }

    /**
     * Implements ASM ClassVisitor.
     */
    private class CallersClassVisitor extends ClassVisitor {
        CallersClassVisitor() {
            super(Opcodes.ASM8);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                         String[] exceptions) {
            // asm javadoc says to return a new instance each time
            return new CallersMethodVisitor();
        }
    }

    /**
     * Implements ASM MethodVisitor.
     */
    private class CallersMethodVisitor extends MethodVisitor {
        CallersMethodVisitor() {
            super(Opcodes.ASM8);
        }

        @Override
        public void visitMethodInsn(
                final int opcode,
                final String owner,
                final String name,
                final String descriptor,
                final boolean isInterface) {
            methodCalled(owner, "methodInsn", owner, name, descriptor);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            // log("\t" + owner + " " + name + " " + desc);
            fieldCalled(owner, "fieldInsn", owner, name, desc);
        }

        @Override
        public void visitInsn(int opcode) {
            super.visitInsn(opcode);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
//            if(type != null){
//                methodCalled(type, "method-try-catch", );
//            }
            super.visitTryCatchBlock(start, end, handler, type);
        }
    }

    private class PackageClassVisitor extends ClassVisitor {
        private String ownerClass;
        public final Map<String, String> foundAcegi = new HashMap<>();

        public PackageClassVisitor() {
            super(Opcodes.ASM8);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);

            this.ownerClass = name;

            classCalled(name, "visit-name", name);
            classCalled(superName, "visit-super", name);

            //            visitClassName(name, "visit-name");
            //            visitClassName(superName, "visit-super", new HashMap<String, String>() {{
            //                put("classInherit", name);
            //            }});
            for (String anInterface : interfaces) {
                classCalled(anInterface, "visit-interface", name);
                //                visitClassName(anInterface, "visit-interface", new HashMap<String, String>() {{
                //                    put("classInherit", name);
                //                }});
            }
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            super.visitInnerClass(name, outerName, innerName, access);

            //            classCalled(outerName, "inner-class", name);
            //            visitClassName(name);
            //            visitClassName(outerName, "inner-class", new HashMap<String, String>() {{
            //                put("fullName", name);
            //            }});
            //            visitClassName(innerName);
        }

        // visit methods, like SecurityComponents createSecurityComponents(), from ActiveDirectorySecurityRealm // and $1 internal class when methodName is null
        @Override
        public void visitOuterClass(String owner, String methodName, String methodDescriptor) {
            super.visitOuterClass(owner, methodName, methodDescriptor);
            classCalled(ownerClass, "outer-class", owner);

            if (methodDescriptor != null) {
                SignatureReader sr = new SignatureReader(methodDescriptor);
                sr.accept(new SignatureMethodVisitor(methodDescriptor, ownerClass, methodName));
            }

            //            visitClassName(owner, "outer-class", new HashMap<String, String>() {{
            //                put("fullName", name);
            //            }});
            //            visitClassName(name);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            PackageFieldVisitor v = new PackageFieldVisitor(ownerClass, name, descriptor);
            v.triggerSignatureVisit();
            return v;
        }

        //descriptor compared to signature, does not contain generic information
        // also, for enum, the descriptor contains the implicit String name 
        @Override
        public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature, String[] exceptions) {
            if (exceptions != null) {
                for (String e : exceptions) {
                    methodCalled(e, "method-exception", ownerClass, methodName, descriptor);
                    //                    visitClassName(e, "method-exception", new HashMap<String, String>() {{
                    //                        put("ownerClass", ownerClass);
                    //                        put("methodName", name);
                    //                    }});
                }
            }

            PackageMethodVisitor v = new PackageMethodVisitor(ownerClass, methodName, descriptor);
            v.triggerSignatureVisit();
            return v;
        }

        private class PackageFieldVisitor extends FieldVisitor {
            private String ownerClass;
            private String fieldName;
            private String fieldSignature;

            PackageFieldVisitor(String ownerClass, String fieldName, String fieldSignature) {
                super(Opcodes.ASM8);
                this.ownerClass = ownerClass;
                this.fieldName = fieldName;
                this.fieldSignature = fieldSignature;
            }

            @Override
            public void visitAttribute(Attribute attribute) {
                super.visitAttribute(attribute);
            }

            public void triggerSignatureVisit() {
                SignatureReader sr = new SignatureReader(fieldSignature);
                sr.acceptType(new SignatureFieldVisitor(fieldSignature, ownerClass, fieldName));
            }
        }

        private class SignatureFieldVisitor extends SignatureVisitor {
            private String fieldSignature;
            private String ownerClass;
            private String fieldName;

            public SignatureFieldVisitor(String fieldSignature, String ownerClass, String fieldName) {
                super(Opcodes.ASM8);
                this.fieldSignature = fieldSignature;
                this.ownerClass = ownerClass;
                this.fieldName = fieldName;
            }

            @Override
            public void visitClassType(String name) {
                super.visitClassType(name);
                fieldCalled(name, "field-classType", ownerClass, fieldName, fieldSignature);
                //                visitClassName(name, "field", new HashMap<String, String>() {{
                //                    put("ownerClass", ownerClass);
                //                    put("fieldName", fieldName);
                //                }});
            }

            @Override
            public void visitInnerClassType(String name) {
                super.visitInnerClassType(name);
                fieldCalled(name, "field-inner", ownerClass, fieldName, fieldSignature);
                //                visitClassName(name, "field-inner", new HashMap<String, String>() {{
                //                    put("ownerClass", ownerClass);
                //                    put("fieldName", fieldName);
                //                }});
            }
        }

        private class PackageMethodVisitor extends MethodVisitor {
            private String ownerClass;
            private String methodName;
            private String methodSignature;

            PackageMethodVisitor(String ownerClass, String methodName, String methodSignature) {
                super(Opcodes.ASM8);
                this.ownerClass = ownerClass;
                this.methodName = methodName;
                this.methodSignature = methodSignature;
            }

            @Override
            public void visitAttribute(Attribute attribute) {
                super.visitAttribute(attribute);
            }

            @Override
            public void visitParameter(String name, int access) {
                super.visitParameter(name, access);
            }

            @Override
            public void visitMethodInsn(
                    final int opcode,
                    final String owner,
                    final String name,
                    final String descriptor,
                    final boolean isInterface) {
                methodCalled(owner, "methodInsn", ownerClass, methodName, methodSignature);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                // log("\t" + owner + " " + name + " " + desc);
                fieldCalled(owner, "fieldInsn", ownerClass, methodName, methodSignature);
            }

            @Override
            public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
                super.visitLocalVariable(name, descriptor, signature, start, end, index);

                SignatureReader sr = new SignatureReader(descriptor);
                sr.acceptType(new SignatureLocalVarVisitor(descriptor, ownerClass, name, methodName));
                SignatureReader sr2 = new SignatureReader(signature);
                sr2.acceptType(new SignatureLocalVarVisitor(descriptor, ownerClass, name, methodName));
            }

            @Override
            public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                if(type != null){
                    methodCalled(type, "method-try-catch", ownerClass, methodName, methodSignature);
                }
            }

            public void triggerSignatureVisit() {
                SignatureReader sr = new SignatureReader(methodSignature);
                sr.accept(new SignatureMethodVisitor(methodSignature, ownerClass, methodName));
            }
        }

        private class SignatureMethodVisitor extends SignatureVisitor {
            private String methodDesc;
            private String ownerClass;
            private String methodName;

            public SignatureMethodVisitor(String methodDesc, String ownerClass, String methodName) {
                super(Opcodes.ASM8);
                this.methodDesc = methodDesc;
                this.ownerClass = ownerClass;
                this.methodName = methodName;
            }

            // Parameter classes
            @Override
            public void visitClassType(String name) {
                super.visitClassType(name);

                methodCalled(name, "method-classType", ownerClass, methodName, methodDesc);

                //                visitClassName(name, "field", new HashMap<String, String>() {{
                //                    put("ownerClass", ownerClass);
                //                    put("methodName", methodName);
                //                }});
            }

            @Override
            public void visitInnerClassType(String name) {
                super.visitInnerClassType(name);
                methodCalled(name, "method-innerClassType", ownerClass, methodName, methodDesc);
                //                visitClassName(name, "field-inner", new HashMap<String, String>() {{
                //                    put("ownerClass", ownerClass);
                //                    put("methodName", methodName);
                //                }});
            }

            @Override
            public void visitFormalTypeParameter(String name) {
                super.visitFormalTypeParameter(name);
                methodCalled(name, "method-formatTypeParam", ownerClass, methodName, methodDesc);
                //                visitClassName(name, "method-param", new HashMap<String, String>() {{
                //                    put("ownerClass", ownerClass);
                //                    put("methodName", methodName);
                //                }});
            }

            @Override
            public void visitTypeVariable(String name) {
                super.visitTypeVariable(name);
                methodCalled(name, "method-typeVar", ownerClass, methodName, methodDesc);
                //                visitClassName(name, "method-var", new HashMap<String, String>() {{
                //                    put("ownerClass", ownerClass);
                //                    put("methodName", methodName);
                //                }});
            }
        }

        private class SignatureLocalVarVisitor extends SignatureVisitor {
            private String fieldDescriptor;
            private String ownerClass;
            private String localVarName;
            private String methodName;

            public SignatureLocalVarVisitor(String fieldDescriptor, String ownerClass, String localVarName, String methodName) {
                super(Opcodes.ASM8);
                this.fieldDescriptor = fieldDescriptor;
                this.ownerClass = ownerClass;
                this.localVarName = localVarName;
                this.methodName = methodName;
            }

            @Override
            public void visitClassType(String name) {
                super.visitClassType(name);
                fieldCalled(name, "field-classType", ownerClass, localVarName, fieldDescriptor);
                //                visitClassName(name, "local-var", new HashMap<String, String>() {{
                //                    put("ownerClass", ownerClass);
                //                    put("localVarName", localVarName);
                //                    put("methodName", methodName);
                //                }});
            }

            @Override
            public void visitInnerClassType(String name) {
                super.visitInnerClassType(name);
                fieldCalled(name, "field-innerClassType", ownerClass, localVarName, fieldDescriptor);
                //                visitClassName(name, "local-var-inner", new HashMap<String, String>() {{
                //                    put("ownerClass", ownerClass);
                //                    put("localVarName", localVarName);
                //                    put("methodName", methodName);
                //                }});
            }

            @Override
            public void visitFormalTypeParameter(String name) {
                super.visitFormalTypeParameter(name);
                fieldCalled(name, "field-formatTypeParam", ownerClass, localVarName, fieldDescriptor);
                //                visitClassName(name, "local-var-param", new HashMap<String, String>() {{
                //                    put("ownerClass", ownerClass);
                //                    put("localVarName", localVarName);
                //                    put("methodName", methodName);
                //                }});
            }

            @Override
            public void visitTypeVariable(String name) {
                super.visitTypeVariable(name);
                fieldCalled(name, "field-typeVar", ownerClass, localVarName, fieldDescriptor);
                //                visitClassName(name, "local-var-var", new HashMap<String, String>() {{
                //                    put("ownerClass", ownerClass);
                //                    put("localVarName", localVarName);
                //                    put("methodName", methodName);
                //                }});
            }
        }
    }
}
