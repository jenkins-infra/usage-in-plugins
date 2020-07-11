package org.jenkinsci.deprecatedusage;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implements ASM ClassVisitor.
 */
public class PackageClassVisitor extends ClassVisitor {
    private String ownerClass;
    public final Map<String, String> foundAcegi = new HashMap<>();

    public PackageClassVisitor() {
        super(Opcodes.ASM8);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);

        this.ownerClass = name;

        visitClassName(name, "visit-name");
        visitClassName(superName, "visit-super", new HashMap<String, String>() {{
            put("classInherit", name);
        }});
        for (String anInterface : interfaces) {
            visitClassName(anInterface, "visit-interface", new HashMap<String, String>() {{
                put("classInherit", name);
            }});
        }
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        super.visitInnerClass(name, outerName, innerName, access);

        //            visitClassName(name);
        visitClassName(outerName, "inner-class", new HashMap<String, String>() {{
            put("fullName", name);
        }});
        //            visitClassName(innerName);
    }

    @Override
    public void visitOuterClass(String owner, String name, String descriptor) {
        super.visitOuterClass(owner, name, descriptor);
        visitClassName(owner, "outer-class", new HashMap<String, String>() {{
            put("fullName", name);
        }});
        //            visitClassName(name);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        PackageFieldVisitor v = new PackageFieldVisitor(ownerClass, name, descriptor);
        v.triggerSignatureVisit();
        return v;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (exceptions != null) {
            for (String e : exceptions) {
                visitClassName(e, "method-exception", new HashMap<String, String>() {{
                    put("ownerClass", ownerClass);
                    put("methodName", name);
                }});
            }
        }

        PackageMethodVisitor v = new PackageMethodVisitor(ownerClass, name, descriptor);
        v.triggerSignatureVisit();
        return v;
    }

    private void visitClassName(String className, String type) {
        visitClassName(className, type, new HashMap<>());
    }

    private void visitClassName(String className, String type, Map<String, String> args) {
        if (className == null) {
            return;
        }
        boolean flagged = DeprecatedApi.isFromAcegi(className);
        if (flagged) {
            foundAcegi.put(className, type + "\t" + args.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining(", ")));
        }
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
            sr.acceptType(new SignatureFieldVisitor(ownerClass, fieldName));
        }
    }

    private class SignatureFieldVisitor extends SignatureVisitor {
        private String ownerClass;
        private String fieldName;

        public SignatureFieldVisitor(String ownerClass, String fieldName) {
            super(Opcodes.ASM8);
            this.ownerClass = ownerClass;
            this.fieldName = fieldName;
        }

        @Override
        public void visitClassType(String name) {
            super.visitClassType(name);
            visitClassName(name, "field", new HashMap<String, String>() {{
                put("ownerClass", ownerClass);
                put("fieldName", fieldName);
            }});
        }

        @Override
        public void visitInnerClassType(String name) {
            super.visitInnerClassType(name);
            visitClassName(name, "field-inner", new HashMap<String, String>() {{
                put("ownerClass", ownerClass);
                put("fieldName", fieldName);
            }});
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
        public void visitCode() {
            super.visitCode();
        }

        @Override
        public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
            super.visitLocalVariable(name, descriptor, signature, start, end, index);

            SignatureReader sr = new SignatureReader(descriptor);
            sr.acceptType(new SignatureLocalVarVisitor(ownerClass, name, methodName));
        }

        public void triggerSignatureVisit() {
            SignatureReader sr = new SignatureReader(methodSignature);
            sr.accept(new SignatureMethodVisitor(ownerClass, methodName));
        }
    }

    private class SignatureMethodVisitor extends SignatureVisitor {
        private String ownerClass;
        private String methodName;

        public SignatureMethodVisitor(String ownerClass, String methodName) {
            super(Opcodes.ASM8);
            this.ownerClass = ownerClass;
            this.methodName = methodName;
        }

        @Override
        public void visitClassType(String name) {
            super.visitClassType(name);
            visitClassName(name, "field", new HashMap<String, String>() {{
                put("ownerClass", ownerClass);
                put("methodName", methodName);
            }});
        }

        @Override
        public void visitInnerClassType(String name) {
            super.visitInnerClassType(name);
            visitClassName(name, "field-inner", new HashMap<String, String>() {{
                put("ownerClass", ownerClass);
                put("methodName", methodName);
            }});
        }

        @Override
        public void visitFormalTypeParameter(String name) {
            super.visitFormalTypeParameter(name);
            visitClassName(name, "method-param", new HashMap<String, String>() {{
                put("ownerClass", ownerClass);
                put("methodName", methodName);
            }});
        }

        @Override
        public void visitTypeVariable(String name) {
            super.visitTypeVariable(name);
            visitClassName(name, "method-var", new HashMap<String, String>() {{
                put("ownerClass", ownerClass);
                put("methodName", methodName);
            }});
        }
    }

    private class SignatureLocalVarVisitor extends SignatureVisitor {
        private String ownerClass;
        private String localVarName;
        private String methodName;

        public SignatureLocalVarVisitor(String ownerClass, String localVarName, String methodName) {
            super(Opcodes.ASM8);
            this.ownerClass = ownerClass;
            this.localVarName = localVarName;
            this.methodName = methodName;
        }

        @Override
        public void visitClassType(String name) {
            super.visitClassType(name);
            visitClassName(name, "local-var", new HashMap<String, String>() {{
                put("ownerClass", ownerClass);
                put("localVarName", localVarName);
                put("methodName", methodName);
            }});
        }

        @Override
        public void visitInnerClassType(String name) {
            super.visitInnerClassType(name);
            visitClassName(name, "local-var-inner", new HashMap<String, String>() {{
                put("ownerClass", ownerClass);
                put("localVarName", localVarName);
                put("methodName", methodName);
            }});
        }

        @Override
        public void visitFormalTypeParameter(String name) {
            super.visitFormalTypeParameter(name);
            visitClassName(name, "local-var-param", new HashMap<String, String>() {{
                put("ownerClass", ownerClass);
                put("localVarName", localVarName);
                put("methodName", methodName);
            }});
        }

        @Override
        public void visitTypeVariable(String name) {
            super.visitTypeVariable(name);
            visitClassName(name, "local-var-var", new HashMap<String, String>() {{
                put("ownerClass", ownerClass);
                put("localVarName", localVarName);
                put("methodName", methodName);
            }});
        }
    }
}
