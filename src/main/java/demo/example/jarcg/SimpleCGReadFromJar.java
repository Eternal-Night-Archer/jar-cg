package demo.example.jarcg;

import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.cast.java.translator.jdt.ecj.ECJClassLoaderFactory;
import com.ibm.wala.classLoader.*;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.strings.StringStuff;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarFile;

public class SimpleCGReadFromJar {
    public static void main(String args[])
            throws IOException, WalaException,IllegalArgumentException, CallGraphBuilderCancelException {
        // JarPath is set by -DJarPath="the absolute path to jar" as a VM Option, such as "/home/haha/1.jar"
        // or "D:\\exericise\\javaExercise\\JavaDemoOnlyBasis\\out\\artifacts\\JavaDemoOnlyBasis_jar\\telephony-common.jar";
        String jarpath = System.getProperty("JarPath");

        // init analysis scope and load necessary libs
        JavaSourceAnalysisScope scope = new JavaSourceAnalysisScope();
        String[] stdlibs = WalaProperties.getJ2SEJarFiles();
        for (int i = 0; i < stdlibs.length; i++) {
            scope.addToScope(ClassLoaderReference.Primordial, new JarFile(stdlibs[i]));
        }

        // get input stream of jar
        InputStream jarIS = new FileInputStream(jarpath);
        scope.addInputStreamForJarToScope(ClassLoaderReference.Application, jarIS);
        System.out.println("scope's application loader: " + scope.getApplicationLoader());

        // cha
        IClassHierarchy cha = ClassHierarchyFactory.make(
                scope, new ECJClassLoaderFactory(scope.getExclusions()));
        System.out.println("cha has " + cha.getNumberOfClasses() + " classes");

        // get potential root class that has main method
        ArrayList<IClass> entryClasses = autoDetectEntryClasses(cha);
        System.out.println("entry classes are: " + entryClasses);
        IClass root = entryClasses.get(0);

        Collection<Entrypoint> entries = new ArrayList<Entrypoint>();
        for (IMethod m : root.getDeclaredMethods()) {
            System.out.println("method name: " + m.getName());
            if (m.isPublic()) {
                entries.add(new DefaultEntrypoint(m, cha));
            }
        }

        // scope options
        AnalysisOptions options = new AnalysisOptions();
        options.setEntrypoints(entries);

        // build cg
        AnalysisCache cache = new AnalysisCacheImpl();
        System.out.println("=========begin build call graph========");
        CallGraphBuilder builder = Util.makeZeroOneContainerCFABuilder(options, cache, cha, scope);
        CallGraph cg = builder.makeCallGraph(options, null);
        System.out.println("some status about cg: " + CallGraphStats.getStats(cg));
    }

    /**
     * detect entry classes by check whether a class has "main" method
     * @param cha
     * @return a collection of IClass
     */
    public static ArrayList<IClass> autoDetectEntryClasses(IClassHierarchy cha) {
        ArrayList<IClass> result = new ArrayList<IClass>();
        for (Iterator<IClass> ic = cha.iterator(); ic.hasNext();) {
            IClass c = ic.next();
            if (!isTakenIntoConsideration(c)) {
                continue;
            }
            c.getAllMethods().forEach(m -> {
                if (m.getName().toString().equals("main")) {
                    result.add(c);
                }
            });
        }
        System.out.println(result.size());
        return result;
    }

    /**
     * get the given class by class descriptor in String(maybe)
     * @param cha
     * @param clsName such as "demo.example.SimpleCGReadFromJar"
     * @return
     */
    public static IClass getGivenClass(IClassHierarchy cha, String clsName) {
        IClass c = cha.lookupClass(TypeReference.findOrCreate(
                ClassLoaderReference.Application,
                StringStuff.deployment2CanonicalTypeString(clsName)
        ));
        System.out.println("given class has " + c.getAllMethods().size() + " method(s).");
        return c;
    }

    /**
     * check whether a class need taken into consideration
     * when building a call graph
     * @param c
     * @return
     */
    public static boolean isTakenIntoConsideration(IClass c) {
        String cName = c.getName().toString();
        ArrayList<String> exclusion = new ArrayList<String>(Arrays.asList(
                "Ljava", "Lcom/sun", "Lorg/graalvm/", "Lsun/",
                "Ljdk/", "Lorg/ietf/", "Lorg/jcp", "Lorg/w3c"
        ));
        for (String ex : exclusion) {
            if (cName.contains(ex)) {
                return false;
            }
        }
        return true;
    }
}
