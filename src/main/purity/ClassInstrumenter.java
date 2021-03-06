package purity;

import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.TraceClassVisitor;


public class ClassInstrumenter implements ClassFileTransformer {

  /**
   * this method is invoked for every
   * class that the JVM is about to load!
   */
  @Override
  public byte[] transform(ClassLoader loader, String className,
      Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
      byte[] classfileBuffer) throws IllegalClassFormatException {

    byte[] result = classfileBuffer;

    try {

      if (Config.PACKAGE == null || className.contains(Config.PACKAGE.replace('.', '/'))) {
        
        if (Config.PRINT_NAME_CLASS == true) System.out.println("instrumenting..." +  className);
              
        // building class node object
        ClassReader cr = new ClassReader(classfileBuffer);
        ClassNode cnode = new ClassNode(Opcodes.ASM4);
        cr.accept(cnode, 0);

        // transforming class node object
        PurityTransformer transformer = new PurityTransformer();

        try {

          transformer.transform(cnode);
        }
        catch (RuntimeException ex) {
          ex.printStackTrace();
          throw ex;
        }

        // building JVM bytecodes
        ClassWriter cw = new ClassWriter(0);

        if (Config.PRINT_INSTRUMENTATED_CODE == true) {
          TraceClassVisitor tracer = new TraceClassVisitor(cw, new PrintWriter(System.out));
          cnode.accept(tracer);
        } else {
          cnode.accept(cw);
        }

        // spitting bytecodes out
        result = cw.toByteArray();
      }

    } catch (Exception exc) {
      exc.printStackTrace();
      System.err.println("fatal error");
      System.exit(1);
    }

    return result;

  }
}