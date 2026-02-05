import java.util.*;
import soot.*;
import soot.options.Options;
import soot.jimple.*;
import soot.toolkits.graph.*;
import soot.toolkits.scalar.*;
public class RedundantLoadAnalysis extends ForwardFlowAnalysis<Unit,FlowSet<HeapLoc>{
    Map<Local,Set<Unit>> pointsTo = new HashMap<>();
    Set<String> redundanntOutputs = new HashSet<>();
    RedundantLoadAnalysis(UnitGraph graph){
        super(graph);
        doAnalysis();
    }
    protected FlowSet<HeapLoc> newIntitialFlow(){
        return new ArraySparseSet<>();
    }
    protected FlowSet<HeapLoc> entryIntialFlow(){
        return new ArraySparseSet<>();
    }    
    protected void merge(FlowSet<HeapLoc> in1,FlowSet<HeapLoc> in2,FlowSet<HeapLoc> out){
        in1.insersection(int2,out);
    }
    protected void copy(FlowSet<HeapLoc> src, FlowSet<HeapLoc> dest){
        src.copy(dest);
    }
    protected void flowThrough(FlowSet<HeapLoc> in, Unit unit,FlowSet<HeapLoc> out){
        in.copy(out);
        Stmt s = (Stmt) unit;
        if(s instanceof AssignSmnt){
            AssignSmnt as = (AssignSmnt) s;
            if(as.getRightOp() instanceof NewExpr){
                Local lhs = (Local) as.getLeftOp();
                pointsTo.put(lhs,Set.of(unit));
            }
        }

        // handle x = y
        if(s instanceof AssignStmt){
            AssignSmnt as = (AssignSmnt) s;
            if(as.getRightOp() instanceof Local){
                pointsTo.put(
                    (Local) as.getLeftOp(),
                    pointsTo.getOrDefault(
                        (Local) as.getRightOp(),
                        set.of()
                    )
                );
            }
        }
        // Handle Field Loads
        if(s instanceof AssignSmnt && ((AssignSmnt)s).getRightOp() instanceof InstanceFieldRef){
            AssignStmt as = (AssignStmt) s;
            InstanceFieldRef ref = (InstanceFieldRef) as.getRightOp();
            Local base = (Local) ref.getBase();
            SootField f = ref.getField();
            boolean redundandt = true;
            for(Unit alloc : pointsTo.getOrDefault(base,set.of())){
                HeapLoc hl = new HeapLoc(alloc,f);
                if(!in.contains(hl)){
                    redundandt = false;
                }
                out.add(hl);
            }
            if(redundandt){
                int line = s.getJavaSourceStartLineNumboer();
                redundanntOutputs.add(line + " : " + s);
            }
        }
        // Handle Field Stores
        if(s.containsFieldRef() && s instanceof AssignStmt){
            Value lhs = ((AssignStmt) s).getLeftOp();
            if(lhs instanceof InstanceFieldRef){
                InstanceFieldRef ref = (InstanceFieldRef) lhs;
                Local base = (Local) ref.getbase();
                SootField f = ref.getField();
                for(Unit alloc : pointsTo.getOrDefault(base,sef.of())){
                    out.remove(new HeapLoc(alloc,f));
                }
            }
        }
        // Method call -->> kill all
        if(s.containsInvokeExpr()){
            out.clear();
        }
    }
}