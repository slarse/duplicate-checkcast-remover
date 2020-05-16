package se.slar.castremover;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InstructionTargeter;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.TargetLostException;
import org.apache.bcel.util.InstructionFinder;

import java.nio.file.Paths;
import java.util.Iterator;

/**
 *  Main class for the duplicate checkcast remover. Contains the whole application.
 *
 *  The code is adapted from the "Peephole optimizer" example in the
 *  <a href="https://commons.apache.org/proper/commons-bcel/manual/appendix.html">BCEL manual</a>.
 *
 *  The purpose of the application is to remove duplicated CHECKCAST instructions that may appear when redundant
 *  parentheses are used in a cast expression. This appears to be a bug that affects at least OpenJDK8.
 *
 *  For example, the cast <code>(Integer) obj;</code> will generate a single CHECKCAST instruction. However,
 *  enclose <code>obj</code> in parentheses, and two identical CHECKCAST instructions are created. That is to say,
 *  <code>(Integer) (obj);</code> cause an extra, redundant CHECKCAST to be generated.
 *
 * @author Simon Larsén
 */
public class Main {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("usage: duplicate-checkcast-remover <CLASSFILE>");
            System.exit(1);
        }

        String filename = args[0];
        if (!Paths.get(filename).toFile().isFile()) {
            System.err.println("No such file" + filename);
            System.exit(1);
        }

        removeDuplicateCheckcasts(filename, filename);
    }

    /**
     * Remove any duplicated CHECKCAST instructions from the classfile and dump the results to output.
     *
     * @param filename Path to the classfile.
     * @param output Path to the output file.
     */
    private static void removeDuplicateCheckcasts(String filename, String output) {
        try {
            JavaClass clazz = new ClassParser(filename).parse();
            Method[] methods = clazz.getMethods();
            ConstantPoolGen cp = new ConstantPoolGen(clazz.getConstantPool());

            for (int i = 0; i < methods.length; i++) {
                if (!(methods[i].isAbstract() || methods[i].isNative())) {
                    MethodGen mg = new MethodGen(methods[i], clazz.getClassName(), cp);
                    Method stripped = removeDuplicateCheckcasts(mg);

                    if (stripped != null)
                        methods[i] = stripped;
                }
            }

            clazz.setConstantPool(cp.getFinalConstantPool());
            clazz.dump(output);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Remove any duplicated CHECKCAST instructions from the given method.
     *
     * @param mg A method generator.
     * @return A new method without duplicated CHECKCAST instructions, or null if there were no duplicates.
     */
    private static Method removeDuplicateCheckcasts(MethodGen mg) {
        InstructionList il = mg.getInstructionList();
        InstructionFinder f = new InstructionFinder(il);
        String pat = "CHECKCAST CHECKCAST"; // match exactly two consecutive checkcast instructions
        int count = 0;

        for (Iterator<InstructionHandle[]> iter = f.search(pat); iter.hasNext();) {
            InstructionHandle[] match = iter.next();


            InstructionHandle first = match[0];
            InstructionHandle second = match[1];

            try {
                il.delete(second);
                count++;
            } catch (TargetLostException e) {
                for (InstructionHandle target : e.getTargets()) {
                    for (InstructionTargeter targeter : target.getTargeters()) {
                        targeter.updateTarget(target, first);
                    }
                }
            }
        }

        Method m = null;

        if (count > 0) {
            System.out.println("Removed " + count + " duplicated CHECKCAST instructions from "
                    + mg.getClassName() + "#" + mg.getName());
            m = mg.getMethod();
            il.dispose(); // Reuse instruction handles
        }

        return m;
    }
}
