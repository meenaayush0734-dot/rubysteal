import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class ParseCheck {
    public static void main(String[] a) throws Exception {
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> dc = new DiagnosticCollector<>();
        StandardJavaFileManager fm = jc.getStandardFileManager(dc, null, null);
        List<File> files;
        try (Stream<Path> s = Files.walk(Paths.get(a[0]))) {
            files = s.filter(p -> p.toString().endsWith(".java")).map(Path::toFile).collect(Collectors.toList());
        }
        JavacTask t = (JavacTask) jc.getTask(null, fm, dc, List.of(), null, fm.getJavaFileObjectsFromFiles(files));
        t.parse();
        long errs = dc.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).count();
        dc.getDiagnostics().forEach(d -> System.out.println(d));
        System.out.println("files=" + files.size() + " syntax errors=" + errs);
    }
 
}
