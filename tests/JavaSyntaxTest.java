import com.sun.source.util.JavacTask;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.util.stream.Collectors;

/** Syntax-only parse: works even if Android SDK is unavailable. */
public class JavaSyntaxTest {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<File> sources;
            try (var paths = Files.walk(Path.of("app/src/main/java"))) {
                sources = paths.filter(p -> p.toString().endsWith(".java"))
                        .map(Path::toFile).collect(Collectors.toList());
            }
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("-proc:none"), null, files.getJavaFileObjectsFromFiles(sources));
            task.parse();
            long errors = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR).count();
            for (var diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) System.err.println(diagnostic);
            }
            if (errors > 0) throw new AssertionError(errors + " Java syntax errors");
            System.out.println("PASS: parsed " + sources.size() + " Java source files");
        }
    }
}
