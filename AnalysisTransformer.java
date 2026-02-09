import java.util.*;
import soot.*;
import soot.jimple.AnyNewExpr;
import soot.jimple.Ref;
import soot.toolkits.graph.*;
import soot.toolkits.scalar.FlowSet;
public class AnalysisTransformer extends BodyTransformer{
    protected void internalTransform(Body body, String phaseName,Map<String,String> options){
         SootMethod m = body.getMethod();

        // ignore constructors & libs
        if (m.isConstructor() || m.isJavaLibraryMethod())
            return;

        UnitGraph graph = new ExceptionalUnitGraph(body);
        new AvailableFieldLoadAnalysis(graph);
    }
}