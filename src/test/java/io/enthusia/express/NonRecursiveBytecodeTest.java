package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

/** Checks project bytecode calls, including lambda implementation handles. */
class NonRecursiveBytecodeTest {
  /** Verifies that project calls have no cycles. */
  @Test void projectCallsHaveNoCycles() throws Exception {
    Map<String, Set<String>> graph = new HashMap<>();
    try (JarFile jar = new JarFile(System.getProperty("pluginJar"))) {
      for (var entries = jar.entries(); entries.hasMoreElements();) {
        var entry = entries.nextElement();
        if (!entry.getName().startsWith("io/enthusia/express/") || !entry.getName().endsWith(".class")) continue;
        try (var input = jar.getInputStream(entry)) {
          new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
            String owner;
            public void visit(int v, int a, String n, String s, String p, String[] i) { owner = n; }
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
              var calls = graph.computeIfAbsent(owner + "." + n + d, k -> new HashSet<>());
              return new MethodVisitor(Opcodes.ASM9) {
                public void visitMethodInsn(int op, String o, String name, String desc, boolean itf) {
                  calls.add(o + "." + name + desc);
                }
                public void visitInvokeDynamicInsn(String name, String desc, Handle bootstrap, Object... args) {
                  for (Object arg : args) if (arg instanceof Handle h) calls.add(h.getOwner() + "." + h.getName() + h.getDesc());
                }
              };
            }
          }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
      }
    }
    assertTrue(graph.size() > 100, "Must inspect the compiled plugin");
    assertEquals(Set.of(), cyclicMethods(graph));
  }
  /** Verifies that detector finds direct and indirect cycles. */

  @Test void detectorFindsDirectAndIndirectCycles() {
    assertEquals(Set.of("a", "b", "c"), cyclicMethods(Map.of("a", Set.of("a"), "b", Set.of("c"), "c", Set.of("b"), "d", Set.of("a"))));
    assertEquals(Set.of(), cyclicMethods(Map.of("a", Set.of("b"), "b", Set.of())));
  }

  private static Set<String> cyclicMethods(Map<String, Set<String>> graph) {
    Set<String> cycles = new TreeSet<>();
    for (String start : graph.keySet()) {
      Set<String> visited = new HashSet<>();
      Deque<String> pending = new ArrayDeque<>(graph.get(start));
      while (!pending.isEmpty()) {
        String next = pending.removeFirst();
        if (next.equals(start)) { cycles.add(start); break; }
        if (visited.add(next)) pending.addAll(graph.getOrDefault(next, Set.of()));
      }
    }
    return cycles;
  }
}
