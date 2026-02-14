import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.*;
import soot.toolkits.scalar.*;
import soot.options.Options;
import soot.jimple.internal.*;

import java.util.*;
import java.util.stream.Collectors;
import java.io.*;

public class PA2 {
    
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java PA2 <TestcaseFolder>");
            return;
        }
        
        String testFolder = args[0];
        
        // Configure Soot
        G.reset();
        Options.v().set_prepend_classpath(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_soot_classpath(".:./soot-4.6.0-jar-with-dependencies.jar:./" + testFolder);
        Options.v().set_process_dir(Collections.singletonList("./" + testFolder));
        Options.v().set_whole_program(false);
        Options.v().set_keep_line_number(true);
        Options.v().set_output_format(Options.output_format_none);
        
        // Set source precedence to class files
        Options.v().set_src_prec(Options.src_prec_class);
        
        // Load necessary classes
        Scene.v().loadNecessaryClasses();
        
        // Store results: Map<ClassName, Map<MethodName, List<RedundantLoad>>>
        Map<String, Map<String, List<RedundantLoadInfo>>> results = new TreeMap<>();
        
        // Analyze each application class
        for (SootClass sootClass : Scene.v().getApplicationClasses()) {
            String className = sootClass.getName();
            
            for (SootMethod method : sootClass.getMethods()) {
                // Skip constructors
                if (method.getName().equals("<init>") || method.getName().equals("<clinit>")) {
                    continue;
                }
                
                // Skip methods without concrete implementation
                if (!method.isConcrete()) {
                    continue;
                }
                
                // Try to retrieve the body
                Body body = null;
                try {
                    body = method.retrieveActiveBody();
                } catch (RuntimeException e) {
                    // If retrieval fails, skip this method
                    System.err.println("Warning: Could not retrieve body for " + method.getName() + ": " + e.getMessage());
                    continue;
                }
                
                if (body == null) {
                    continue;
                }
                
                // Perform redundant load analysis
                List<RedundantLoadInfo> redundantLoads = analyzeMethod(method);
                
                if (!redundantLoads.isEmpty()) {
                    results.putIfAbsent(className, new TreeMap<>());
                    results.get(className).put(method.getName(), redundantLoads);
                }
            }
        }
        
        // Print results
        printResults(results);
    }
    
   private static List<RedundantLoadInfo> analyzeMethod(SootMethod method) {

    boolean DEBUG = true;

    List<RedundantLoadInfo> redundantLoads = new ArrayList<>();

    Body body = method.getActiveBody();
    UnitGraph graph = new BriefUnitGraph(body);

    if (DEBUG) {
        System.out.println("\n===========================================");
        System.out.println("Analyzing Method: " + method.getSignature());
        System.out.println("===========================================\n");
    }

    // Perform points-to analysis
    PointsToAnalysis pointsTo = new PointsToAnalysis(graph, body);

    // Perform available loads analysis
    AvailableLoadsAnalysis availableLoads =
            new AvailableLoadsAnalysis(graph, pointsTo);

    List<Unit> units = new ArrayList<>();
    for (Unit u : body.getUnits()) {
        units.add(u);
    }

    for (int i = 0; i < units.size(); i++) {

        Unit unit = units.get(i);
        Stmt stmt = (Stmt) unit;

        if (DEBUG) {
            System.out.println("------------------------------------------------");
            System.out.println("UNIT: " + unit);
            System.out.println("Line: " + stmt.getJavaSourceStartLineNumber());
        }

        if (stmt instanceof AssignStmt) {

            AssignStmt assign = (AssignStmt) stmt;
            Value rightOp = assign.getRightOp();
            Value leftOp = assign.getLeftOp();

            if (rightOp instanceof InstanceFieldRef) {

                InstanceFieldRef fieldRef = (InstanceFieldRef) rightOp;
                Value base = fieldRef.getBase();
                SootField field = fieldRef.getField();

                if (DEBUG) {
                    System.out.println("FIELD LOAD DETECTED: " +
                            base + "." + field.getName());
                }

                Set<FieldLoad> availableBefore =
                        availableLoads.getFlowBefore(unit);

                
                Set<String> currentFieldPts = pointsTo.getFieldPointsToSet(base, field, unit);

                if (DEBUG) {
                    System.out.println("Available Loads BEFORE:");
                    for (FieldLoad fl : availableBefore) {
                        System.out.println("   " + fl);
                    }

                    System.out.println("Current field Points-To: " + currentFieldPts);
                }

                for (FieldLoad availLoad : availableBefore) {

                    Set<String> availFieldPts =
                                        pointsTo.getFieldPointsToSet(
                                            availLoad.base,
                                            availLoad.field,
                                            unit
                                        );

                    boolean sameBase =
                            base.equals(availLoad.base);
                    boolean sameField = field.getName().equals(availLoad.field.getName());

                     // Compute intersection
                    Set<String> intersection = new HashSet<>(currentFieldPts);
                    intersection.retainAll(availFieldPts);

                    boolean mayAlias =
                            !intersection.isEmpty()
                            ||
                            (currentFieldPts.isEmpty()
                             && availFieldPts.isEmpty()
                             && sameBase && sameField);

                    if (DEBUG) {
                        System.out.println("\nComparing with: " + availLoad);
                        System.out.println("   Avail Base  fields Points-To: "
                                + availFieldPts);
                        System.out.println("   sameBase: " + sameBase);
                        System.out.println("   mayAlias: " + mayAlias);
                    }

                    if (mayAlias) {
                        
                        int lineNumber =
                                stmt.getJavaSourceStartLineNumber();

                        if (lineNumber <= 0) break;

                        String fieldRefStr =
                                base.toString() + ".<" +
                                field.getDeclaringClass().getName() + ": " +
                                field.getType() + " " +
                                field.getName() + ">";

                        String replacementVar =
                                availLoad.target.toString();

                        if (DEBUG) {
                            System.out.println(">>> REDUNDANT LOAD FOUND!");
                            System.out.println("    Replacing with: "
                                    + replacementVar);
                        }

                        redundantLoads.add(new RedundantLoadInfo(
                                lineNumber,
                                fieldRefStr,
                                replacementVar,
                                replacementVar
                        ));

                        break;
                    }
                }
            }
        }
    }

    Collections.sort(redundantLoads);

    if (DEBUG) {
        System.out.println("\n======= REDUNDANT LOAD SUMMARY =======");
        for (RedundantLoadInfo info : redundantLoads) {
            System.out.println(info);
        }
        System.out.println("======================================\n");
    }

    return redundantLoads;
}

    
    private static boolean isFieldLoad(Stmt stmt) {
        if (stmt instanceof AssignStmt) {
            AssignStmt assign = (AssignStmt) stmt;
            return assign.getRightOp() instanceof InstanceFieldRef;
        }
        return false;
    }
    
    private static InstanceFieldRef getFieldRef(Stmt stmt) {
        AssignStmt assign = (AssignStmt) stmt;
        return (InstanceFieldRef) assign.getRightOp();
    }
    
    private static Value getLoadTarget(Stmt stmt) {
        AssignStmt assign = (AssignStmt) stmt;
        return assign.getLeftOp();
    }
    
    private static void printResults(Map<String, Map<String, List<RedundantLoadInfo>>> results) {
        for (Map.Entry<String, Map<String, List<RedundantLoadInfo>>> classEntry : results.entrySet()) {
            String className = classEntry.getKey();
            
            for (Map.Entry<String, List<RedundantLoadInfo>> methodEntry : classEntry.getValue().entrySet()) {
                String methodName = methodEntry.getKey();
                List<RedundantLoadInfo> loads = methodEntry.getValue();
                
                System.out.println(className + ": " + methodName);
                for (RedundantLoadInfo load : loads) {
                    // Format: LineNumber: FieldLoadStatement ReplacementVariable;
                    System.out.println(load.lineNumber + ": " + load.jimpleStmt + " " + load.replacementVar + ";");
                }
            }
        }
    }
    
    // Helper class to store redundant load information
    static class RedundantLoadInfo implements Comparable<RedundantLoadInfo> {
        int lineNumber;
        String jimpleStmt;
        String targetVar;
        String replacementVar;
        
        RedundantLoadInfo(int lineNumber, String jimpleStmt, String targetVar, String replacementVar) {
            this.lineNumber = lineNumber;
            this.jimpleStmt = jimpleStmt;
            this.targetVar = targetVar;
            this.replacementVar = replacementVar;
        }
        
        @Override
        public int compareTo(RedundantLoadInfo other) {
            return Integer.compare(this.lineNumber, other.lineNumber);
        }
    }
    
    // Represents a field load
    static class FieldLoad {
        Value base;
        SootField field;
        Value target;
        
        FieldLoad(Value base, SootField field, Value target) {
            this.base = base;
            this.field = field;
            this.target = target;
        }
        
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FieldLoad)) return false;
            FieldLoad other = (FieldLoad) o;
            return base.equals(other.base) && field.equals(other.field);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(base, field);
        }
        
        @Override
        public String toString() {
            return base + "." + field.getName() + " -> " + target;
        }
    }
}

// Intraprocedural Field-Sensitive Points-to Analysis
class PointsToAnalysis extends ForwardFlowAnalysis<Unit, Map<Value, Set<String>>> {
    
    private Body body;
    private Map<Unit, Map<Value, Set<String>>> unitToPointsTo;

    // Heap: Object -> (field -> pointsTo set)
    private Map<String, Map<String, Set<String>>> heap;
    
    public PointsToAnalysis(UnitGraph graph, Body body) {
        super(graph);
        this.body = body;
        this.unitToPointsTo = new HashMap<>();
        this.heap = new HashMap<>();
        doAnalysis();
    }
    
    @Override
    protected void flowThrough(Map<Value, Set<String>> in,
                               Unit unit,
                               Map<Value, Set<String>> out) {

        // Copy in → out
        copy(in, out);
        
        Stmt stmt = (Stmt) unit;
        
        if (stmt instanceof AssignStmt) {

            AssignStmt assign = (AssignStmt) stmt;
            Value left = assign.getLeftOp();
            Value right = assign.getRightOp();

            /* =============================
               1. x = new T()
               ============================= */
            if (right instanceof NewExpr) {

                String allocSite = "O" + unit.getJavaSourceStartLineNumber();

                Set<String> pointsTo = new HashSet<>();
                pointsTo.add(allocSite);

                out.put(left, pointsTo);

                // Initialize heap entry
                heap.putIfAbsent(allocSite, new HashMap<>());
            }

            /* =============================
               2. x = y
               ============================= */
            else if (right instanceof Local && left instanceof Local) {

                Set<String> pointsTo =
                        out.getOrDefault(right, Collections.emptySet());

                out.put(left, new HashSet<>(pointsTo));
            }

            /* =============================
               3. x = o.f   (FIELD LOAD)
               ============================= */
            else if (right instanceof InstanceFieldRef && left instanceof Local) {

                InstanceFieldRef fieldRef = (InstanceFieldRef) right;
                Value base = fieldRef.getBase();
                String fieldName = fieldRef.getField().getName();

                Set<String> result = new HashSet<>();

                Set<String> baseObjects =
                        out.getOrDefault(base, Collections.emptySet());

                for (String obj : baseObjects) {

                    Map<String, Set<String>> fieldMap = heap.get(obj);

                    if (fieldMap != null) {
                        Set<String> fieldPointsTo = fieldMap.get(fieldName);
                        if (fieldPointsTo != null) {
                            result.addAll(fieldPointsTo);
                        }
                    }
                }

                out.put(left, result);
            }

            /* =============================
               4. o.f = x   (FIELD STORE)
               ============================= */
            else if (left instanceof InstanceFieldRef) {

                InstanceFieldRef fieldRef = (InstanceFieldRef) left;
                Value base = fieldRef.getBase();
                String fieldName = fieldRef.getField().getName();

                Set<String> baseObjects =
                        out.getOrDefault(base, Collections.emptySet());

                Set<String> rightPointsTo =
                        out.getOrDefault(right, Collections.emptySet());

                for (String obj : baseObjects) {

                    heap.putIfAbsent(obj, new HashMap<>());

                    Map<String, Set<String>> fieldMap = heap.get(obj);
                     if (baseObjects.size() == 1) {
                            // 🔥 STRONG UPDATE
                            fieldMap.put(fieldName, new HashSet<>(rightPointsTo));
                        } else {
                            // ⚠️ WEAK UPDATE
                            fieldMap.putIfAbsent(fieldName, new HashSet<>());
                            fieldMap.get(fieldName).addAll(rightPointsTo);
                    }
                }
            }
        }
        

        // Store result for this unit
        unitToPointsTo.put(unit, deepCopy(out));
        System.out.println("=================================================");
        System.out.println("UNIT: " + unit);
        System.out.println("Line: " + unit.getJavaSourceStartLineNumber());
        System.out.println("-------------------------------------------------");

        System.out.println("OUT (Variable -> Objects):");

        for (Map.Entry<Value, Set<String>> entry : out.entrySet()) {
            System.out.println("   " + entry.getKey() + " -> " + entry.getValue());
        }

        System.out.println();

        System.out.println("HEAP (Object -> Field -> Objects):");

        for (Map.Entry<String, Map<String, Set<String>>> objEntry : heap.entrySet()) {

            String obj = objEntry.getKey();
            System.out.println("   " + obj);

            Map<String, Set<String>> fieldMap = objEntry.getValue();

            for (Map.Entry<String, Set<String>> fieldEntry : fieldMap.entrySet()) {
                System.out.println("      ." + fieldEntry.getKey()
                        + " -> " + fieldEntry.getValue());
            }
        }

        System.out.println("=================================================\n");



    }
    
    @Override
    protected Map<Value, Set<String>> newInitialFlow() {
        return new HashMap<>();
    }
    
    @Override
    protected Map<Value, Set<String>> entryInitialFlow() {
        return new HashMap<>();
    }
    
    @Override
    protected void merge(Map<Value, Set<String>> in1,
                            Map<Value, Set<String>> in2,
                            Map<Value, Set<String>> out) {

            out.clear();

            Set<Value> allVars = new HashSet<>();
            allVars.addAll(in1.keySet());
            allVars.addAll(in2.keySet());

            for (Value var : allVars) {

                Set<String> set1 = in1.getOrDefault(var, Collections.emptySet());
                Set<String> set2 = in2.getOrDefault(var, Collections.emptySet());

                // UNION for points-to
                Set<String> union = new HashSet<>(set1);
                union.addAll(set2);

                out.put(var, union);
            }
        }


    
    @Override
    protected void copy(Map<Value, Set<String>> source,
                        Map<Value, Set<String>> dest) {

        dest.clear();
        for (Map.Entry<Value, Set<String>> entry : source.entrySet()) {
            dest.put(entry.getKey(),
                     new HashSet<>(entry.getValue()));
        }
    }

    // Deep copy helper
    private Map<Value, Set<String>> deepCopy(Map<Value, Set<String>> original) {
        Map<Value, Set<String>> copy = new HashMap<>();
        for (Map.Entry<Value, Set<String>> e : original.entrySet()) {
            copy.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        return copy;
    }
    
    public Set<String> getPointsToSet(Value var, Unit unit) {
        Map<Value, Set<String>> pointsTo = unitToPointsTo.get(unit);
        if (pointsTo != null && pointsTo.containsKey(var)) {
            return pointsTo.get(var);
        }
        return Collections.emptySet();
    }
    public Set<String> getFieldPointsToSet(Value base,
                                        SootField field,
                                        Unit u) {

        Set<String> result = new HashSet<>();

        // Step 1: get objects base may point to
        Set<String> baseObjects = getPointsToSet(base, u);

        String fieldName = field.getName();

        for (String obj : baseObjects) {

            Map<String, Set<String>> fieldMap = heap.get(obj);
            if (fieldMap == null) continue;

            Set<String> targets = fieldMap.get(fieldName);
            if (targets != null) {
                result.addAll(targets);
            }
        }

        return result;
    }
    public Set<String> getReachableObjects(Set<String> roots) {

        Set<String> visited = new HashSet<>(roots);
        Queue<String> worklist = new LinkedList<>(roots);

        while (!worklist.isEmpty()) {
            String obj = worklist.poll();

            Map<String, Set<String>> fields = heap.get(obj);
            if (fields == null) continue;

            for (Set<String> targets : fields.values()) {
                for (String t : targets) {
                    if (visited.add(t))
                        worklist.add(t);
                }
            }
        }

        return visited;
    }


}


// Available Loads Analysis
class AvailableLoadsAnalysis extends ForwardFlowAnalysis<Unit, Set<PA2.FieldLoad>> {
    
    private PointsToAnalysis pointsTo;
    
    public AvailableLoadsAnalysis(UnitGraph graph, PointsToAnalysis pointsTo) {
        super(graph);
        this.pointsTo = pointsTo;
        doAnalysis();
    }
    
    @Override
    protected void flowThrough(Set<PA2.FieldLoad> in, Unit unit, Set<PA2.FieldLoad> out) {
        Stmt stmt = (Stmt) unit;
        
        // Start with incoming available loads
        copy(in, out);
        
        // Kill loads that are invalidated
        if (stmt instanceof AssignStmt) {
            AssignStmt assign = (AssignStmt) stmt;
            Value left = assign.getLeftOp();
            
            // If we're writing to a field, kill all loads of that field
            if (left instanceof InstanceFieldRef) {
                InstanceFieldRef fieldRef = (InstanceFieldRef) left;
                Value base = fieldRef.getBase();
                SootField field = fieldRef.getField();
                Set<String> basePointsTo = pointsTo.getPointsToSet(base, unit);
                
                // Kill all loads that may alias with this write
                out.removeIf(load -> {
                    Set<String> loadBasePointsTo = pointsTo.getPointsToSet(load.base, unit);
                    return !Collections.disjoint(basePointsTo, loadBasePointsTo) && 
                           load.field.equals(field);
                });
            }
            
            // If we're reading from a field, generate this load
            Value right = assign.getRightOp();
            if (right instanceof InstanceFieldRef) {
                InstanceFieldRef fieldRef = (InstanceFieldRef) right;
                PA2.FieldLoad newLoad = new PA2.FieldLoad(
                    fieldRef.getBase(),
                    fieldRef.getField(),
                    left
                );
                out.add(newLoad);
            }
        }
            /* =============================
        3️⃣ Method call kill
        ============================= */
        if (stmt.containsInvokeExpr()) {

            InvokeExpr invoke = stmt.getInvokeExpr();

            Set<Value> receiverObjs = new HashSet<>();

            // receiver
            if (invoke instanceof InstanceInvokeExpr) {
                receiverObjs.add(
                    ((InstanceInvokeExpr) invoke).getBase()
                );
            }

            // arguments
            for (Value arg : invoke.getArgs()) {
                receiverObjs.add(arg);
            }
            // Step 1: convert Value → abstract objects
            Set<String> receiverHeapObjs = new HashSet<>();

            for (Value v : receiverObjs) {
                receiverHeapObjs.addAll(
                    pointsTo.getPointsToSet(v, stmt)
                );
            }

           
            Set<String> reachable = pointsTo.getReachableObjects(receiverHeapObjs);
            System.out.println("Call stmt: " + stmt);
            System.out.println("Receiver heap objs: " + receiverHeapObjs);
            System.out.println("Reachable: " + reachable);


            out.removeIf(load -> {
                       Set<String> objs =
                       pointsTo.getPointsToSet(load.base,
                       unit);
                     System.out.println("Checking load: " + load); 
                     System.out.println("Load resolves to: " + objs);
                    return out.contains(new PA2.FieldLoad(load.base,load.field,null));
            });

        }

    }
    
    @Override
    protected Set<PA2.FieldLoad> newInitialFlow() {
        return new HashSet<>();
    }
    
    @Override
    protected Set<PA2.FieldLoad> entryInitialFlow() {
        return new HashSet<>();
    }
    
    @Override
    protected void merge(Set<PA2.FieldLoad> in1, Set<PA2.FieldLoad> in2, Set<PA2.FieldLoad> out) {
        out.clear();
        // Intersection: only loads available on all paths
        out.addAll(in1);
        out.retainAll(in2);
    }
    
    @Override
    protected void copy(Set<PA2.FieldLoad> source, Set<PA2.FieldLoad> dest) {
        dest.clear();
        dest.addAll(source);
    }
}