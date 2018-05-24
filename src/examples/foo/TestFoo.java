package foo;

import java.io.IOException;
import java.util.ArrayList;

import org.junit.Test;

public class TestFoo {

  class A {
    int x;
    
    int f() { // pure
      int a = x;
      return a;
    }
    
    void g() { // inpure
      x = 3; 
    }

    void h(java.util.ArrayList<Integer> l) { // inpure
      l.add(10); 
    }

    ArrayList<String> i() { // should be pure but is inpure
      ArrayList<String> l = new ArrayList<String>();
      l.add("Hello");
      return l;
    }

  }

  @Test(expected=IOException.class)
  public void testOne() throws IOException {
    A a = new A();
    a.x = 10;
    a.g(); 
    a.h(new java.util.ArrayList<Integer>());
    a.i();
    if (true) throw new IOException();
  }

  //TODO: check why this does not get printed
  public void testTwo() throws IOException {
    A a = new A();
    a.h(new java.util.ArrayList<Integer>());
  }



}
