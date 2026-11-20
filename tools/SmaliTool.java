import com.android.tools.smali.baksmali.Baksmali;
import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import com.android.tools.smali.smali.smaliFlexLexer;
import com.android.tools.smali.smali.smaliParser;
import com.android.tools.smali.smali.smaliTreeWalker;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.tree.CommonTreeNodeStream;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class SmaliTool {
    static final int API = 28;

    public static void main(String[] args) throws Exception {
        Opcodes opcodes = new Opcodes(API, 0);
        if (args[0].equals("dis")) {
            File dex = new File(args[1]);
            File outDir = new File(args[2]);
            var container = DexFileFactory.loadDexContainer(dex, opcodes);
            String entryName = (String) container.getDexEntryNames().get(0);
            DexBackedDexFile dexFile = container.getEntry(entryName).getDexFile();
            BaksmaliOptions opts = new BaksmaliOptions();
            opts.debugInfo = true;
            Baksmali.disassembleDexFile(dexFile, outDir, 4, opts);
            System.out.println("disassembled to " + outDir);
        } else if (args[0].equals("asm")) {
            Path root = Paths.get(args[1]);
            File outDex = new File(args[2]);
            List<Path> files;
            try (Stream<Path> s = Files.walk(root)) {
                files = s.filter(p -> p.toString().endsWith(".smali")).sorted().collect(Collectors.toList());
            }
            System.out.println("assembling " + files.size() + " smali files");
            DexBuilder dexBuilder = new DexBuilder(opcodes);
            int errors = 0;
            for (Path p : files) {
                try (InputStreamReader reader = new InputStreamReader(new FileInputStream(p.toFile()), "UTF-8")) {
                    smaliFlexLexer lexer = new smaliFlexLexer(reader, API);
                    CommonTokenStream tokens = new CommonTokenStream(lexer);
                    smaliParser parser = new smaliParser(tokens);
                    var result = parser.smali_file();
                    CommonTreeNodeStream treeStream = new CommonTreeNodeStream(result.tree);
                    treeStream.tokens = tokens;
                    smaliTreeWalker walker = new smaliTreeWalker(treeStream);
                    walker.opcodes = opcodes;
                    walker.dexBuilder = dexBuilder;
                    walker.smali_file();
                } catch (Throwable t) {
                    System.err.println("ERROR in " + p + ": " + t);
                    errors++;
                }
            }
            if (errors > 0) {
                System.err.println("ABORT: " + errors + " file(s) with errors");
                System.exit(1);
            }
            dexBuilder.writeTo(new FileDataStore(outDex));
            System.out.println("wrote " + outDex + " (" + outDex.length() + " bytes)");
        }
    }
}
