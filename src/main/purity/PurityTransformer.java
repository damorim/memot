package purity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

public class PurityTransformer {
  
  /** method of interest **/
  String location;
  
  @SuppressWarnings("unchecked")
  public void transform(ClassNode cn) {

    for (MethodNode mn : (List<MethodNode>) cn.methods) {
      
      InsnList insns = mn.instructions;
      if (insns.size() == 0) { 
        continue;
      }
      
      // find exception handlers
      Iterator<AbstractInsnNode> j = insns.iterator();
      List<LabelNode> exceptionHandlers = new ArrayList<LabelNode>();
      for (int i = 0; i < mn.tryCatchBlocks.size(); i++) {
        LabelNode lnode = ((TryCatchBlockNode) mn.tryCatchBlocks.get(i)).handler;
        if (lnode != null) {
          exceptionHandlers.add(lnode);
        }
      }

      location = cn.name + "." + mn.name + mn.desc;

      while (j.hasNext()) {
        AbstractInsnNode in = j.next();
        int op = in.getOpcode();

        /** notify on returns and throws **/
        if (op == Opcodes.ATHROW) {
          
          // notify on explicit throws
          InsnList il = createThrowNotification(cn, mn);
          insns.insert(in.getPrevious(), il);

        } else if ((op >= Opcodes.IRETURN && op <= Opcodes.RETURN) || op == Opcodes.RET) {
          
          // notify on exits
          InsnList il = createMethodExitNotification(cn, mn);
          insns.insert(in.getPrevious(), il);

        } else if (exceptionHandlers.contains(in)) {
          
          // notify on catches
          InsnList il = createCatchNotification(cn, mn);
          AbstractInsnNode place = getNextRelevant(j);
          insns.insert(place.getPrevious(), il);          

        } else if (op >= Opcodes.IASTORE && op <= Opcodes.SASTORE) {

          InsnList list = createArrayAccessNotification(op, location);
          insns.insertBefore(in, list);
          insns.remove(in);

        } else if (op == Opcodes.PUTFIELD || op == Opcodes.PUTSTATIC) {

          FieldInsnNode node = (FieldInsnNode)in;
          // class name
          String owner = node.owner;
          // field name
          String fieldName = node.name;
          
          InsnList list = createFieldAccessNotification(op==Opcodes.PUTSTATIC, true/*isStore*/, owner, fieldName, location);
          insns.insert(in.getPrevious(), list);
          
        } else if (op == Opcodes.NEW || op == Opcodes.NEWARRAY || op == Opcodes.ANEWARRAY || op == Opcodes.MULTIANEWARRAY) {

          InsnList list = createAllocationNotification(op);
          insns.insert(in.getNext(), list);

        } else if (op >= Opcodes.INVOKEVIRTUAL && op <= Opcodes.INVOKEDYNAMIC) {
          
          MethodInsnNode mNode = (MethodInsnNode) in;
          String completeName = mNode.owner.replace('/', '.') + "." + mNode.name;// + mNode.desc;
          InsnList list = createInvocationNotification(completeName);
          insns.insert(in.getPrevious(), list);
          
        }        

      } // done iterating instructions

      // notify on method entry 
      InsnList il = createMethodEntryNotification(cn, mn);      
      insns.insert(il); 
      mn.maxStack += 10;

    } 
  }

  
  private void debug(AbstractInsnNode in) {
    AbstractInsnNode tmp = in;
    do {
      System.out.println(tmp);
      tmp = tmp.getNext();
    } while (tmp != null);
  }  

  private AbstractInsnNode getNextRelevant(Iterator<AbstractInsnNode> it) {
    AbstractInsnNode insn = null;
    while (it.hasNext()) {
      insn = it.next();
      if (insn != null && !(insn instanceof LineNumberNode)) {
        break;
      }
    }
    if (insn == null) {
      throw new RuntimeException();
    }
    return insn;
  }
  
  
  /*************** generation of instrumentation code ****************/

  static String OBSERVER_CLASS_STR = "purity/PurityTransformer";

  @Deprecated
  private InsnList createAllocationNotification(int op) {
    InsnList il = new InsnList();
    il.add(new InsnNode(Opcodes.DUP));
    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OBSERVER_CLASS_STR, "alloc", "(Ljava/lang/Object;)V"));
    return il;
  }


  private InsnList createInvocationNotification(String methodName) {
    InsnList il = new InsnList();
    il.add(new LdcInsnNode(methodName));
    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OBSERVER_CLASS_STR, "meth_call", "(Ljava/lang/String;)V"));
    return il;
  }

  @Deprecated
  private InsnList createGeneralNotification(ClassNode cn, MethodNode mn, String name) {
    InsnList il = new InsnList();
    String fullyQmName = "L" + cn.name + ";" + mn.name + mn.desc;
    il.add(new LdcInsnNode(fullyQmName));
    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OBSERVER_CLASS_STR, name, "(Ljava/lang/String;)V"));
    return il;
  }

  private InsnList createMethodEntryNotification(ClassNode cn, MethodNode mn) {
    return createGeneralNotification(cn, mn, "inapp_meth_call");
  }

  private InsnList createMethodExitNotification(ClassNode cn, MethodNode mn) {
    return createGeneralNotification(cn, mn, "inapp_meth_return");
  }

  private InsnList createThrowNotification(ClassNode cn, MethodNode mn) {
    return createGeneralNotification(cn, mn, "throwException");
  }

  private InsnList createCatchNotification(ClassNode cn, MethodNode mn) {
    return createGeneralNotification(cn, mn, "catchException");  
  }
  
  @Deprecated
  private InsnList createFieldAccessNotification(boolean isStatic, boolean isStore, String className, String fieldName, String source) {
    InsnList il = new InsnList();    
    String adviceMethodName;
    if (isStore) {
      adviceMethodName = "put";
    } else {
      adviceMethodName = "get";
    }
    // if (isStatic) {
    //   adviceMethodName += "Static";
    // } else {      
    //   adviceMethodName += "NonStatic";
    // }
    // filling instruction list with other arguments
    String signature;
    if (isStatic) {
      signature = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V";
    } else { /*instance*/
      signature = "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V";
      if (isStore) {
        il.add(new InsnNode(Opcodes.DUP2));
        il.add(new InsnNode(Opcodes.POP));
      } else {
        il.add(new InsnNode(Opcodes.DUP));
      }
    }
    il.add(new LdcInsnNode(className));
    il.add(new LdcInsnNode(fieldName));
    il.add(new LdcInsnNode(source));    
    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OBSERVER_CLASS_STR, adviceMethodName, signature));
    return il;
  }
  
  @Deprecated
  private InsnList createArrayAccessNotification(int op, String location) {
    InsnList il = new InsnList();    
    // filling instruction list with other arguments
    String adviceMethodName;
    String signature;
    switch (op) {
    /* stores */
    case Opcodes.IASTORE:
      adviceMethodName = "iastore";
      signature = "([IIILjava/lang/String;)V";      
      break;
    case Opcodes.LASTORE:
      adviceMethodName = "lastore";
      signature = "([LILLjava/lang/String;)V";      
      break;
    case Opcodes.FASTORE:
      adviceMethodName = "fastore";
      signature = "([FIFLjava/lang/String;)V";      
      break;
    case Opcodes.DASTORE:
      adviceMethodName = "dastore";
      signature = "([DIDLjava/lang/String;)V";      
      break;
    case Opcodes.AASTORE:
      adviceMethodName = "aastore";
      signature = "([Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/String;)V";      
      break;
    case Opcodes.BASTORE:
      adviceMethodName = "bastore";
      signature = "([BIBLjava/lang/String;)V";      
      break;
    case Opcodes.CASTORE:
      adviceMethodName = "castore";
      signature = "([CICLjava/lang/String;)V";      
      break;
    case Opcodes.SASTORE:
      adviceMethodName = "sastore";
      signature = "([SISLjava/lang/String;)V";      
      break;
    /* loads */
    case Opcodes.IALOAD:
      adviceMethodName = "iaload";
      signature = "([IILjava/lang/String;)I";      
      break;
    case Opcodes.LALOAD:
      adviceMethodName = "laload";
      signature = "([LILjava/lang/String;)L";      
      break;
    case Opcodes.FALOAD:
      adviceMethodName = "faload";
      signature = "([FILjava/lang/String;)F";      
      break;
    case Opcodes.DALOAD:
      adviceMethodName = "daload";
      signature = "([DILjava/lang/String;)D";      
      break;
    case Opcodes.AALOAD:
      adviceMethodName = "aaload";
      signature = "([Ljava/lang/Object;ILjava/lang/String;)Ljava/lang/Object;";      
      break;
    case Opcodes.BALOAD:
      adviceMethodName = "baload";
      signature = "([BILjava/lang/String;)B";      
      break;
    case Opcodes.CALOAD:
      adviceMethodName = "caload";
      signature = "([CILjava/lang/String;)C";      
      break;
    case Opcodes.SALOAD:
      adviceMethodName = "saload";
      signature = "([SILjava/lang/String;)S";      
      break;      
      
    default:
      throw new UnsupportedOperationException();
    }
    il.add(new LdcInsnNode(location));
    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OBSERVER_CLASS_STR, adviceMethodName, signature));
    return il;
  }
  
  /************************* listeners **********************/
  static class MethodCall {
    String mname;
    long timestamp;
    boolean isPure = true;
    public MethodCall(String mname) {
      this.mname = mname;
      this.timestamp = System.nanoTime();
    }
    public String toString() {
      return String.format("%s %s", mname, isPure?"PURE":"INPURE");
    }
    public void setInpure() {
      isPure = false;
    }
  }
  
  static Map<Object, Long> map = new HashMap<Object, Long>();
  static Stack<MethodCall> callStack = new Stack<MethodCall>();
  static Set<MethodCall> report = new HashSet<MethodCall>();
  private static void dumpReport() {
    for (MethodCall mCall : report) {
      System.out.println(mCall);
    }
  }

  public static void alloc(Object oref) {
    map.put(oref, System.nanoTime());
    if (Config.DEBUG) System.out.printf("object allocation %s (%d)\n", oref, map.get(oref));
  }

  public static void inapp_meth_call(String name) {
    callStack.push(new MethodCall(name));
    if (Config.DEBUG) System.out.printf("call %s, time=%d\n", name, System.nanoTime());
  }

  public static void meth_call(String name) {
    // TODO: this is a conservative approximation. Consider the case an object 
    // is just created in a method and passed as argument to an inpure method. For 
    // example, a list is created and then the method add() is called on that list. 
    // Ideally, we should either (1) instrument the entire library (should be 
    // expensive) or (2) model the side-effects of methods (which params it affects)
    if (Config.isInpure(name)) {
      for(int i=callStack.size()-1; i>=0; i--) {
        MethodCall mcall = callStack.get(i);
        mcall.setInpure();
        report.add(mcall);
      }
    }
  }

  public static void inapp_meth_return(String name) {
    callStack.pop();
    if (Config.DEBUG) System.out.printf("exit %s\n", name);
    if (callStack.size()==0) {
      //TODO: consider reporting this to a file...
      dumpReport();
    }
  }
  
  public static void throwException(String name) {
    if (Config.DEBUG) System.out.printf("throw %s\n", name);
    if (callStack.size()==1) {
      // this call will get lost; remove it!
      callStack.pop();
      dumpReport();
    }
  }

  public static void catchException(String name) {
    // TODO: test this. call stack needs to be reinstated as the 
    // exception may have been thrown in a different method
    MethodCall tmp = callStack.peek();
    while (!tmp.mname.equals(name)); { 
      tmp = callStack.pop();
    } 
    if (Config.DEBUG) System.out.printf("catch %s\n", name);
  }

  public static void put(Object ref, String className, String fieldName, String source) {
    long objDOB = map.get(ref); 
    if (Config.DEBUG) System.out.printf("putfield %s.%s at object with DOB=%d\n", className, fieldName, objDOB);
    for(int i=callStack.size()-1; i>=0; i--) {
      MethodCall mcall = callStack.get(i);
      if (!mcall.isPure) continue;
      if (objDOB > mcall.timestamp) { // mcall is newer than write access -> safe (and so are all calls in the stack)
        break;  
      }
      mcall.setInpure();
      report.add(mcall);
    }
  }
  
  public static void get(Object ref, String className, String fieldName, String source) {
    if (Config.DEBUG) System.out.printf("getfield %s.%s\n", className, fieldName);
  }
  
  /* array accesses */
  public static void iastore(int[] ar, int index, int val, String location) {
    ar[index] = val;
    writeArrayIndex(ar, index, location);
  }
  
  public static void lastore(long[] ar, int index, long val, String location) {
    ar[index] = val;
    writeArrayIndex(ar, index, location);
  }
  
  public static void fastore(float[] ar, int index, float val, String location) {
    ar[index] = val;
    writeArrayIndex(ar, index, location);
  }
  
  public static void dastore(double[] ar, int index, double val, String location) {
    ar[index] = val;
    writeArrayIndex(ar, index, location);
  }
  
  public static void aastore(Object[] ar, int index, Object val, String location) {
    ar[index] = val;
    writeArrayIndex(ar, index, location);
  }
  
  public static void bastore(byte[] ar, int index, byte val, String location) {
    ar[index] = val;
    writeArrayIndex(ar, index, location);
  }
  
  public static void castore(char[] ar, int index, char val, String location) {
    ar[index] = val;
    writeArrayIndex(ar, index, location);
  }
  
  public static void sastore(short[] ar, int index, short val, String location) {
    ar[index] = val;
    writeArrayIndex(ar, index, location);
  }
  
  public static void writeArrayIndex(Object aref, int index, String location) {
    throw new RuntimeException("TODO");
  }
  
  public static int iaload(int[] ar, int index, String location) {
    readArrayIndex(ar, index, location);
    return ar[index];
  }
  
  public static long laload(long[] ar, int index, String location) {
    readArrayIndex(ar, index, location);
    return ar[index];    
  }
  
  public static float faload(float[] ar, int index, String location) {
    
    return ar[index];
  }
  
  public static double daload(double[] ar, int index, String location) {
    readArrayIndex(ar, index, location);
    return ar[index];
  }
  
  public static Object aaload(Object[] ar, int index, String location) {
    readArrayIndex(ar, index, location);
    return ar[index];
  }
  
  public static byte baload(byte[] ar, int index, String location) {
    readArrayIndex(ar, index, location);
    return ar[index];
  }
  
  public static char caload(char[] ar, int index, String location) {
    readArrayIndex(ar, index, location);
    return ar[index];
  }
  
  public static short saload(short[] ar, int index, String location) {
    readArrayIndex(ar, index, location);
    return ar[index];
  }
    
  private static void readArrayIndex(Object aref, int index, String location) {
    throw new RuntimeException("TODO");
  }

}