package purity;

import org.apache.commons.collections4.Trie;
import org.apache.commons.collections4.trie.PatriciaTrie;

public class Config {

  public static boolean PRINT_NAME_CLASS = false;
  public static boolean PRINT_INSTRUMENTATED_CODE = false;
  public static String PACKAGE;
  static boolean DEBUG = false;

  static Trie<String, Void> trie = new PatriciaTrie<Void>();

  /**
   * To avoid instrumenting standard library code, we need to black 
   * list all inpure methods.
   */
  static {
    trie.put("java.util.ArrayList.add", null);
  }

  public static boolean isInpure(String name) {
    return trie.containsKey(name);
  }
  
}