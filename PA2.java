import soot.*;
import soot.options.Options;
public class PA2{
    public static void main(String [] arg){
        // String classPath = "./testcases/" + arg[0];
        // String [] sootArgs = {
        //     "-cp", classPath,
        //     "-pp",
        //     "-f", "J",
        //     "-main-class", "Test",
        //     "-process-dir", classPath
        // };
        String[] sootArgs = {
            "-cp", ".", "-pp",
            "-f", "J",
            "-w",
            "-main-class", "Test",	// specify the main class
            "Test"                 // list the classes to analyze
        };
        AnalysisTransformer analysisTransformer = new AnalysisTransformer();
        PackManager.v().getPack("jtp").add(new Transform("jtp.dfa",analysisTransformer));
        Options.v().set_keep_line_number(true);
        soot.Main.main(sootArgs);
    }
}