import java.util.*;

import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.*;
import soot.toolkits.scalar.*;

public class AvailableFieldLoadAnalysis
        extends ForwardFlowAnalysis<Unit, FlowSet<FieldLoad>> {

    private final FlowSet<FieldLoad> emptySet =
            new ArraySparseSet<>();

    public AvailableFieldLoadAnalysis(UnitGraph graph) {
        super(graph);
        doAnalysis();
    }

    @Override
    protected FlowSet<FieldLoad> newInitialFlow() {
        return emptySet.clone();
    }

    @Override
    protected FlowSet<FieldLoad> entryInitialFlow() {
        return emptySet.clone();
    }

    @Override
    protected void merge(FlowSet<FieldLoad> in1,
                         FlowSet<FieldLoad> in2,
                         FlowSet<FieldLoad> out) {
        // MUST analysis → intersection
        in1.intersection(in2, out);
    }

    @Override
    protected void copy(FlowSet<FieldLoad> src,
                        FlowSet<FieldLoad> dst) {
        src.copy(dst);
    }

    @Override
    protected void flowThrough(FlowSet<FieldLoad> in,
                               Unit unit,
                               FlowSet<FieldLoad> out) {

        in.copy(out);

        /* -------------------------
           Method calls → kill all
         ------------------------- */
        if (unit instanceof InvokeStmt ||
            (unit instanceof AssignStmt &&
             ((AssignStmt) unit).containsInvokeExpr())) {
            out.clear();
            return;
        }

        if (!(unit instanceof AssignStmt)) return;

        AssignStmt stmt = (AssignStmt) unit;
        Value lhs = stmt.getLeftOp();
        Value rhs = stmt.getRightOp();

        /* -------------------------
           Local copy: x = y
         ------------------------- */
        if (lhs instanceof Local && rhs instanceof Local) {
            Local x = (Local) lhs;
            Local y = (Local) rhs;

            List<FieldLoad> toAdd = new ArrayList<>();

            for (Iterator<FieldLoad> it = in.iterator(); it.hasNext();) {
                FieldLoad fl = it.next();
                if (fl.value.equals(y)) {
                    toAdd.add(new FieldLoad(fl.base, fl.field, x));
                }
            }

            for (FieldLoad fl : toAdd) out.add(fl);
            return;
        }

        /* -------------------------
           Field load: x = o.f
         ------------------------- */
        if (lhs instanceof Local &&
            rhs instanceof InstanceFieldRef) {

            Local x = (Local) lhs;
            InstanceFieldRef fr = (InstanceFieldRef) rhs;

            Local base = (Local) fr.getBase();
            SootField field = fr.getField();

            boolean redundant = false;

            for (Iterator<FieldLoad> it = in.iterator(); it.hasNext();) {
                FieldLoad fl = it.next();
                if (fl.base.equals(base) &&
                    fl.field.equals(field)) {
                    redundant = true;
                    break;
                }
            }

            if (redundant) {
                reportRedundant(unit);
            }

            out.add(new FieldLoad(base, field, x));
            return;
        }

        /* -------------------------
           Field store: o.f = x
         ------------------------- */
        if (lhs instanceof InstanceFieldRef) {
            InstanceFieldRef fr = (InstanceFieldRef) lhs;
            Local base = (Local) fr.getBase();
            SootField field = fr.getField();

            List<FieldLoad> toRemove = new ArrayList<>();

            for (Iterator<FieldLoad> it = out.iterator(); it.hasNext();) {
                FieldLoad fl = it.next();
                if (fl.base.equals(base) &&
                    fl.field.equals(field)) {
                    toRemove.add(fl);
                }
            }

            for (FieldLoad fl : toRemove) out.remove(fl);
        }
    }

    private void reportRedundant(Unit u) {
        int line = u.getJavaSourceStartLineNumber();
        System.out.println(
            "[Redundant load] line " + line + ": " + u);
    }
}
