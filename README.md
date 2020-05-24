# duplicate-checkcast-remover
This is a small program with the sole purpose of removing duplicated
[checkcast](https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.10.1.9.checkcast) instructions.

I found that OpenJDK8 would insert two checkcast instructions instead of one in the presence of redundant parentheses.
For example, the cast `(Integer) obj;` will generate a single checkcast instruction. However,
enclose `obj` in parentheses, and two identical checkcast instructions are created. That is to say,
`(Integer) (obj);` causes an extra, redundant checkcast to be generated.

An extra checkcast does nothing functionally, but it messes with the bytecode diff analysis in Spork's evaluation.
This application, largely based on [the "Peephole optimizer" example](https://commons.apache.org/proper/commons-bcel/manual/appendix.html)

## Requirements
Running `duplicate-checkcast-remover` requires a Java runtime version 8 or higher.

## Usage 
Get the jar-file from [the latest release](https://github.com/slarse/duplicate-checkcast-remover/releases/tag/v1.0.1)
and download it. Then run it like so.

```
$ java -jar duplicate-checkcast-remover-1.0.1-jar-with-dependencies.jar <CLASSFILE>
```

## Build
Building requires JDK8+ and Maven. Build the jar with:

```
$ mvn clean compile assembly:single
```

## Example use case
Try compiling this file with OpenJDK8 (I have not been able to reproduce the
problem with any other JDK).

```java
public class Main {
    public static void main(String[] args) {
        Object obj = Integer.valueOf(1);
        Integer i = (Integer) obj;
        System.out.println(i);
    }
}
```

Use `javap -c Main.class` to check the bytecode. It should look something like this.

```
public class Main {
  public Main();
    Code:
       0: aload_0
       1: invokespecial #1                  // Method java/lang/Object."<init>":()V
       4: return

  public static void main(java.lang.String[]);
    Code:
       0: iconst_1
       1: invokestatic  #2                  // Method java/lang/Integer.valueOf:(I)Ljava/lang/Integer;
       4: astore_1
       5: aload_1
       6: checkcast     #3                  // class java/lang/Integer
       9: astore_2
      10: getstatic     #4                  // Field java/lang/System.out:Ljava/io/PrintStream;
      13: aload_2
      14: invokevirtual #5                  // Method java/io/PrintStream.println:(Ljava/lang/Object;)V
      17: return
}
```

Now, insert a redundant parenthesis around `obj`, that is to say, replace `Integer i = (Integer) obj;` with
`Integer i = (Integer) (obj);`. Compile it again and check the bytecode. It should now look like this.

```
public class Main {
  public Main();
    Code:
       0: aload_0
       1: invokespecial #1                  // Method java/lang/Object."<init>":()V
       4: return

  public static void main(java.lang.String[]);
    Code:
       0: iconst_1
       1: invokestatic  #2                  // Method java/lang/Integer.valueOf:(I)Ljava/lang/Integer;
       4: astore_1
       5: aload_1
       6: checkcast     #3                  // class java/lang/Integer
       9: checkcast     #3                  // class java/lang/Integer
      12: astore_2
      13: getstatic     #4                  // Field java/lang/System.out:Ljava/io/PrintStream;
      16: aload_2
      17: invokevirtual #5                  // Method java/io/PrintStream.println:(Ljava/lang/Object;)V
      20: return
}
```

See that it added another checkcast in the main method (index 9)? Strange, right? It makes no functional difference,
and probably doesn't affect performance either. It caused absolute havoc in my thesis project, however, as I needed to
compare classfiles for equality. Rogue parentheses causing the comparison verdicts to become not equal caused major
issues.

Try using `duplicate-checkcast-remover` on the new classfile with the duplicated cast, and then viewing it again.
The duplicated cast will be gone. Try running the file, too, it should still print 1!

# License
This project is licensed under the [Apache 2.0](LICENSE) license. It is almost a straight copy of
[the "Peephole optimizer" example](https://commons.apache.org/proper/commons-bcel/manual/appendix.html).
 
