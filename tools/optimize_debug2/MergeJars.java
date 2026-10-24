import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Builds the jar R8 optimises from two dex-to-class translations of the same APK:
 *  - enjarify supplies the method bodies (dex2jar mistranslates some branches, e.g. "if (0 > x)"
 *    becomes "if (x > 0)", which crashes Compose's MutableVector.contains);
 *  - dex2jar supplies everything enjarify drops: annotations (Jackson, @JavascriptInterface,
 *    kotlin.Metadata), generic signatures and inner-class attributes.
 * dex has no "interface" bit on method references, so calls to static/private/default methods of
 * interfaces are also re-flagged; without that R8 removes their targets. D8's framework API stubs are
 * dropped (see isFrameworkStub).
 *
 * Usage: MergeJars enjarify.jar dex2jar.jar out.jar [library.jar ...]
 */
public class MergeJars {
    public static void main(String[] args) throws Exception {
        Set<String> interfaces = new HashSet<>();
        for (int i = 0; i < args.length; i++) {
            if (i == 1 || i == 2) continue;
            forEachClass(args[i], data -> {
                ClassReader cr = new ClassReader(data);
                if ((cr.getAccess() & Opcodes.ACC_INTERFACE) != 0) interfaces.add(cr.getClassName());
            });
        }
        Map<String, byte[]> meta = new HashMap<>();
        forEachClass(args[1], data -> meta.put(new ClassReader(data).getClassName(), data));

        int[] merged = {0}, codeOnly = {0}, flagged = {0}, stubs = {0};
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(args[2])))) {
            Set<String> seen = new HashSet<>();
            forEachClass(args[0], data -> {
                ClassNode code = new ClassNode();
                new ClassReader(data).accept(code, 0);
                if (isFrameworkStub(code.name)) { stubs[0]++; return; }
                ClassNode result = code;
                byte[] metaBytes = meta.get(code.name);
                if (metaBytes != null) {
                    ClassNode m = new ClassNode();
                    new ClassReader(metaBytes).accept(m, ClassReader.SKIP_FRAMES);
                    Map<String, MethodNode> bodies = new HashMap<>();
                    for (MethodNode mn : code.methods) bodies.put(mn.name + mn.desc, mn);
                    Set<String> covered = new HashSet<>();
                    for (MethodNode mn : m.methods) {
                        MethodNode body = bodies.get(mn.name + mn.desc);
                        if (body == null) continue;
                        covered.add(mn.name + mn.desc);
                        mn.instructions = body.instructions;
                        mn.tryCatchBlocks = body.tryCatchBlocks;
                        mn.maxStack = body.maxStack;
                        mn.maxLocals = body.maxLocals;
                        mn.localVariables = null;
                        mn.visibleLocalVariableAnnotations = null;
                        mn.invisibleLocalVariableAnnotations = null;
                    }
                    for (MethodNode mn : code.methods) {
                        if (!covered.contains(mn.name + mn.desc)) m.methods.add(mn);
                    }
                    m.version = code.version; // enjarify writes 49.0 code without stack map frames
                    m.sourceFile = null;
                    m.sourceDebug = null;
                    result = m;
                    merged[0]++;
                } else {
                    codeOnly[0]++;
                }
                for (MethodNode mn : result.methods) {
                    if (mn.instructions == null) continue;
                    for (AbstractInsnNode insn : mn.instructions) {
                        if (!(insn instanceof MethodInsnNode)) continue;
                        MethodInsnNode call = (MethodInsnNode) insn;
                        if (call.getOpcode() == Opcodes.INVOKEVIRTUAL || call.getOpcode() == Opcodes.INVOKEINTERFACE) continue;
                        boolean want = interfaces.contains(call.owner);
                        if (want != call.itf) { call.itf = want; flagged[0]++; }
                    }
                }
                ClassWriter cw = new ClassWriter(0);
                result.accept(cw);
                String entry = result.name + ".class";
                if (!seen.add(entry)) return;
                out.putNextEntry(new ZipEntry(entry));
                out.write(cw.toByteArray());
                out.closeEntry();
            });
        }
        System.out.println("merged: " + merged[0] + ", code only (no dex2jar metadata): " + codeOnly[0]
                + ", interface call sites fixed: " + flagged[0] + ", framework stubs dropped: " + stubs[0]);
    }

    /**
     * D8 adds empty stand-ins for framework classes newer than minSdk (e.g. WindowInsetsAnimation$Callback,
     * API 30). Left in the program, they hide the real library class from R8, which then strips overrides
     * such as onProgress and the app dies with AbstractMethodError. R8 recreates the stubs it needs.
     */
    static boolean isFrameworkStub(String name) {
        return (name.startsWith("android/") && !name.startsWith("android/support/"))
                || name.startsWith("dalvik/") || name.startsWith("java/") || name.startsWith("javax/");
    }

    interface ClassConsumer { void accept(byte[] data) throws IOException; }

    static void forEachClass(String jar, ClassConsumer c) throws IOException {
        try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(new FileInputStream(jar)))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (e.getName().endsWith(".class")) c.accept(in.readAllBytes());
            }
        }
    }
}
