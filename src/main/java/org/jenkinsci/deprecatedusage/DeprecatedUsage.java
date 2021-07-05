package org.jenkinsci.deprecatedusage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.io.IOUtils;

import org.jenkinsci.deprecatedusage.search.SearchCriteria;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

//TODO rename to remove the Deprecated as it was generalized over time to look for any calls
public class DeprecatedUsage {
    // python-wrapper has wrappers for all extension points and descriptors,
    // they are just wrappers and not real usage
    public static final Set<String> IGNORED_PLUGINS = new HashSet<>(
            Arrays.asList("python-wrapper.hpi"));

    private final Plugin plugin;
    private final boolean includePluginLibraries;
    private final SearchCriteria searchCriteria;

    private final Set<String> classes = new LinkedHashSet<>();
    private final Set<String> methods = new LinkedHashSet<>();
    private final Set<String> fields = new LinkedHashSet<>();

    /**
     * Provider = methods we look for
     * Consumer = methods using a provider
     * And in next level, the consumer becomes the provider
     * 
     * For a given provider method, returns the consumers methods that calls it inside their bodies
     */
    private final Map<String, Set<String>> providerToConsumers = new HashMap<>();

    /**
     * Provider = methods we look for
     * Consumer = methods using a provider
     * And in next level, the consumer becomes the provider
     * 
     * For a given consumer method, returns the provider methods it calls inside its body
     */
    private final Map<String, Set<String>> consumerToProviders = new HashMap<>();

    private final ClassVisitor indexerClassVisitor = new IndexerClassVisitor();
    private final ClassVisitor classVisitor = new CallersClassVisitor();
    private final Map<String, List<String>> superClassAndInterfacesByClass = new HashMap<>();

    public DeprecatedUsage(String pluginName, String pluginVersion, SearchCriteria searchCriteria, boolean includePluginLibraries) {
        super();
        this.plugin = new Plugin(pluginName, pluginVersion);
        this.includePluginLibraries = includePluginLibraries;
        this.searchCriteria = searchCriteria;
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
                if (searchCriteria.isLookingForClass(s)) {
                    classes.add(s);
                } else if (s.length() > 2 && s.charAt(0) == 'L' && s.charAt(s.length() - 1) == ';') {
                    String name = s.substring(1, s.length() - 1);
                    if (searchCriteria.isLookingForClass(name)) {
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

    public Map<String, Set<String>> getProviderToConsumers() {
        return providerToConsumers;
    }

    public Map<String, Set<String>> getConsumerToProviders() {
        return consumerToProviders;
    }

    public Set<String> getNewSignatures() {
        return new HashSet<>(consumerToProviders.keySet());
    }

    public boolean hasDeprecatedUsage() {
        return !classes.isEmpty() || !methods.isEmpty() || !fields.isEmpty();
    }

    void methodCalled(String className, String name, String desc, String callerClassName, String callerName, String callerDesc) {
        if (!shouldAnalyze(className)) {
            return;
        }

        String methodKey = DeprecatedApi.getMethodKey(className, name, desc);

        boolean lookingForClass = searchCriteria.isLookingForClass(className);
        boolean lookingForMethodKey = searchCriteria.isLookingForMethod(methodKey, className, name);
        if (lookingForClass || lookingForMethodKey) {
            if (lookingForClass) {
                classes.add(className);
            }
            if (lookingForMethodKey) {
                methods.add(methodKey);
            }

            String callerSignature = DeprecatedApi.getMethodKey(callerClassName, callerName, callerDesc);
            
            providerToConsumers.computeIfAbsent(methodKey, s -> new HashSet<>()).add(callerSignature);
            consumerToProviders.computeIfAbsent(callerSignature, s -> new HashSet<>()).add(methodKey);
        }

        final List<String> superClassAndInterfaces = superClassAndInterfacesByClass.get(className);
        if (superClassAndInterfaces != null) {
            for (final String superClassOrInterface : superClassAndInterfaces) {
                methodCalled(superClassOrInterface, name, desc, callerClassName, callerName, callerDesc);
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

        return searchCriteria.shouldAnalyzeClass(className);
    }

    void fieldCalled(String className, String name, String desc, String callerClassName, String callerName, String callerDesc) {
        if (!shouldAnalyze(className)) {
            return;
        }

        String fieldKey = DeprecatedApi.getFieldKey(className, name, desc);
        
        boolean lookingForClass = searchCriteria.isLookingForClass(className);
        boolean lookingForFieldKey = searchCriteria.isLookingForField(fieldKey, className, name);
        if (lookingForClass || lookingForFieldKey) {
            if (lookingForClass) {
                classes.add(className);
            }
            if (lookingForFieldKey) {
                fields.add(fieldKey);
            }

            String callerSignature = DeprecatedApi.getMethodKey(callerClassName, callerName, callerDesc);

            // the first pass will be done with fieldKey, but then, they are going to regular recursive method search
            providerToConsumers.computeIfAbsent(fieldKey, s -> new HashSet<>()).add(callerSignature);
            consumerToProviders.computeIfAbsent(callerSignature, s -> new HashSet<>()).add(fieldKey);
        }

        final List<String> superClassAndInterfaces = superClassAndInterfacesByClass.get(className);
        if (superClassAndInterfaces != null) {
            for (final String superClassOrInterface : superClassAndInterfaces) {
                fieldCalled(superClassOrInterface, name, desc, callerClassName, callerName, callerDesc);
            }
        }
    }

    public static boolean isJavaClass(String asmClassName) {
        // if starts with java/ or javax/, then it's a class of core java
        return asmClassName.startsWith("java/") || asmClassName.startsWith("javax/");
    }

    /**
     * Discover the class hierarchy (SuperClass + Interfaces)
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
     * ClassVisitor that delegates method visit to CallersMethodVisitor
     */
    private class CallersClassVisitor extends ClassVisitor {
        //TODO check if ThreadLocal is really required
        private String currentClassName = null;
        
        CallersClassVisitor() {
            super(Opcodes.ASM9);
        }

        @Override 
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            currentClassName = name;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                String[] exceptions) {
            // asm javadoc says to return a new instance each time
            return new CallersMethodVisitor(currentClassName, name, desc, signature);
        }
    }

    /**
     * Visit every methods and fields
     */
    private class CallersMethodVisitor extends MethodVisitor {
        String className;
        String name;
        String desc;
        String signature;
        
        CallersMethodVisitor(String className, String name, String desc, String signature) {
            super(Opcodes.ASM9);
            this.className = className;
            this.name = name;
            this.desc = desc;
            this.signature = signature;
        }

        @Deprecated
        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            methodCalled(owner, name, desc, this.className, this.name, this.desc);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                boolean itf) {
            methodCalled(owner, name, desc, this.className, this.name, this.desc);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            fieldCalled(owner, name, desc, this.className, this.name, this.desc);
        }
    }
}
