package org.jenkinsci.deprecatedusage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.io.IOUtils;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class DeprecatedUsage {
    // python-wrapper has wrappers for all extension points and descriptors,
    // they are just wrappers and not real usage
    public static final Set<String> IGNORED_PLUGINS = new HashSet<>(
            Arrays.asList("python-wrapper.hpi"));

    private final Plugin plugin;
    private final DeprecatedApi deprecatedApi;
    private final boolean includePluginLibraries;

    private final Set<String> classes = new LinkedHashSet<>();
    private final Set<String> methods = new LinkedHashSet<>();
    private final Set<String> fields = new LinkedHashSet<>();
    private final ClassVisitor indexerClassVisitor = new IndexerClassVisitor();
    private final ClassVisitor classVisitor = new CallersClassVisitor();
    private final Map<String, List<String>> superClassAndInterfacesByClass = new HashMap<>();

    public DeprecatedUsage(String pluginName, String pluginVersion, DeprecatedApi deprecatedApi, boolean includePluginLibraries) {
        super();
        this.plugin = new Plugin(pluginName, pluginVersion);
        this.deprecatedApi = deprecatedApi;
        this.includePluginLibraries = includePluginLibraries;
    }

    public void analyze(File pluginFile) throws IOException {
        if (IGNORED_PLUGINS.contains(pluginFile.getName())) {
            return;
        }
        analyzeWithClassVisitor(pluginFile, indexerClassVisitor);
        analyzeWithClassVisitor(pluginFile, classVisitor);
    }

    
    public void analyzeWithClassVisitor(File pluginFile, ClassVisitor aClassVisitor)
            throws IOException {
        // recent plugins package their classes as a jar file with the same name as the war file in
        // WEB-INF/lib/ while older plugins were packaging their classes in WEB-INF/classes/
        try (WarReader warReader = new WarReader(pluginFile, !includePluginLibraries)) {
            String fileName = warReader.nextClass();
            while (fileName != null) {
                try {
                    @SuppressWarnings("resource") // handled by warReader.nextClass()
                    InputStream is = warReader.getInputStream();
                    analyze(is, aClassVisitor);
                } catch (Exception e) {
                    System.err.println("Failed to fully analyze " + pluginFile + ".  " + fileName + " not scanned due to -> ");
                    e.printStackTrace();
                }
                fileName = warReader.nextClass();
            }
        }
    }

    private static final ThreadLocal<char[]> bufs = ThreadLocal.withInitial(() -> new char[99999]);

    private void analyze(InputStream input, ClassVisitor aClassVisitor) throws IOException {
        byte[] data = IOUtils.toByteArray(input);
        final ClassReader classReader = new ClassReader(data);
        char[] buf = bufs.get();
        for (int i = 0; i < classReader.getItemCount(); i++) {
            int offset = classReader.getItem(i);
            if (offset == 0) {
                continue;
            }
            int kind = data[offset - 1];
            if (kind == 1) {
                int length = classReader.readUnsignedShort(offset);
                // Adapted from ClassReader.readUtf, which is private:
                int currentOffset = offset + 2;
                int endOffset = currentOffset + length;
                int strLength = 0;
                while (currentOffset < endOffset) {
                    int currentByte = data[currentOffset++];
                    if ((currentByte & 0x80) == 0) {
                        buf[strLength++] = (char) (currentByte & 0x7F);
                    } else if ((currentByte & 0xE0) == 0xC0) {
                        buf[strLength++] = (char) (((currentByte & 0x1F) << 6) + (data[currentOffset++] & 0x3F));
                    } else {
                        buf[strLength++] = (char) (((currentByte & 0xF) << 12) + ((data[currentOffset++] & 0x3F) << 6) + (data[currentOffset++] & 0x3F));
                    }
                }
                String s = new String(buf, 0, strLength);
                if (deprecatedApi.getClasses().contains(s)) {
                    classes.add(s);
                } else if (s.length() > 2 && s.charAt(0) == 'L' && s.charAt(s.length() - 1) == ';') {
                    String name = s.substring(1, s.length() - 1);
                    if (deprecatedApi.getClasses().contains(name)) {
                        classes.add(name);
                    }
                }
                continue;
            }
        }
        classReader.accept(aClassVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    public Plugin getPlugin() { return plugin; }

    public Set<String> getClasses() {
        return new TreeSet<>(classes);
    }

    public Set<String> getMethods() {
        return new TreeSet<>(methods);
    }

    public Set<String> getFields() {
        return new TreeSet<>(fields);
    }

    public boolean hasDeprecatedUsage() {
        return !classes.isEmpty() || !methods.isEmpty() || !fields.isEmpty();
    }

    void methodCalled(String className, String name, String desc) {

            if (!shouldAnalyze(className)) {
                return;
            }
            if (deprecatedApi.getClasses().contains(className)) {
                classes.add(className);
            } else {
                final String method = DeprecatedApi.getMethodKey(className, name, desc);
                if (deprecatedApi.getMethods().contains(method) ||
                        (Options.get().additionalMethodsFile != null &&
                                Options.getAdditionalMethodNames().getOrDefault(className, Collections.emptySet()).contains(name))) {
                    methods.add(method);
                }
                final List<String> superClassAndInterfaces = superClassAndInterfacesByClass
                        .get(className);
                if (superClassAndInterfaces != null) {
                    for (final String superClassOrInterface : superClassAndInterfaces) {
                        methodCalled(superClassOrInterface, name, desc);
                    }
                }
            }
    }

    /**
     * Returns true if given class should be analyzed
     *
     * @see Options
     */
    private boolean shouldAnalyze(String className)  {

        if (className.endsWith("DefaultTypeTransformation")) {
            // various DefaultTypeTransformation#box signatures seem false positive in plugins written in Groovy
            return false;
        }

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
        if(isJavaClass(className)) {
            return options.includeJavaCoreClasses;
        }

        if(!className.contains("jenkins") && !className.contains("hudson") && !className.contains("org/kohsuke")) {
            return options.onlyIncludeJenkinsClasses;
        }

        return true;
    }

    void fieldCalled(String className, String name, String desc) {
        // Calls to java and javax are ignored first
        if (!isJavaClass(className)) {
            if (deprecatedApi.getClasses().contains(className)) {
                classes.add(className);
            } else {
                final String field = DeprecatedApi.getFieldKey(className, name, desc);
                if (deprecatedApi.getFields().contains(field) ||
                        (Options.get().additionalFieldsFile != null &&
                                Options.getAdditionalFields().getOrDefault(className, Collections.emptySet()).contains(name))) {
                    fields.add(field);
                }
                final List<String> superClassAndInterfaces = superClassAndInterfacesByClass
                        .get(className);
                if (superClassAndInterfaces != null) {
                    for (final String superClassOrInterface : superClassAndInterfaces) {
                        fieldCalled(superClassOrInterface, name, desc);
                    }
                }
            }
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
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            // log(name + " extends " + superName + " {");
            
            final List<String> superClassAndInterfaces = new ArrayList<>();
            // superClass may be null for java.lang.Object and module-info.class
            // Object would have been filtered but we see lots of module-info classes
            if (superName != null && !isJavaClass(superName)) {
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
            super(Opcodes.ASM9);
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
            super(Opcodes.ASM9);
        }

        @Deprecated
        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            // log("\t" + owner + " " + name + " " + desc);
            methodCalled(owner, name, desc);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                boolean itf) {
            // log("\t" + owner + " " + name + " " + desc);
            methodCalled(owner, name, desc);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            // log("\t" + owner + " " + name + " " + desc);
            fieldCalled(owner, name, desc);
        }
    }
}
