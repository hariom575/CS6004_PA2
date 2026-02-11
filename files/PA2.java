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
        List<RedundantLoadInfo> redundantLoads = new ArrayList<>();
        
        Body body = method.getActiveBody();
        UnitGraph graph = new BriefUnitGraph(body);
        
        // Perform points-to analysis
        PointsToAnalysis pointsTo = new PointsToAnalysis(graph, body);
        
        // Perform available loads analysis
        AvailableLoadsAnalysis availableLoads = new AvailableLoadsAnalysis(graph, pointsTo);
        
        // Check each unit for redundant loads
        for (Unit unit : body.getUnits()) {
            Stmt stmt = (Stmt) unit;
            
            // Check if this is a field load
            if (isFieldLoad(stmt)) {
                InstanceFieldRef fieldRef = getFieldRef(stmt);
                Value base = fieldRef.getBase();
                SootField field = fieldRef.getField();
                Value target = getLoadTarget(stmt);
                
                // Get available loads at this point (before the statement)
                Set<FieldLoad> availableBefore = availableLoads.getFlowBefore(unit);
                
                // Get points-to set for the base
                Set<String> basePointsTo = pointsTo.getPointsToSet(base, unit);
                
                // Check if this load is redundant
                for (FieldLoad availLoad : availableBefore) {
                    Set<String> availBasePointsTo = pointsTo.getPointsToSet(availLoad.base, unit);
                    
                    // Check if the objects may alias and fields match
                    // For same variable, must be exact match; otherwise check aliasing
                    boolean sameBase = base.equals(availLoad.base);
                    boolean mayAlias = !Collections.disjoint(basePointsTo, availBasePointsTo);
                    
                    if ((sameBase || mayAlias) && availLoad.field.equals(field)) {
                        
                        // This is a redundant load
                        int lineNumber = stmt.getJavaSourceStartLineNumber();
                        if (lineNumber > 0) {
                            // Extract the field reference part from jimple statement
                            String fieldRefStr = base.toString() + ".<" + 
                                field.getDeclaringClass().getName() + ": " + 
                                field.getType() + " " + field.getName() + ">";
                            
                            String targetVar = target.toString();
                            
                            redundantLoads.add(new RedundantLoadInfo(
                                lineNumber, fieldRefStr, targetVar, availLoad.target.toString()
                            ));
                        }
                        break; // Only report once per load
                    }
                }
            }
        }
        
        // Sort by line number
        Collections.sort(redundantLoads);
        
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
                    System.out.println(load.lineNumber + ": " + load.jimpleStmt + " " + load.targetVar + ";");
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

// Intraprocedural Points-to Analysis
class PointsToAnalysis extends ForwardFlowAnalysis<Unit, Map<Value, Set<String>>> {
    
    private Body body;
    private Map<Unit, Map<Value, Set<String>>> unitToPointsTo;
    
    public PointsToAnalysis(UnitGraph graph, Body body) {
        super(graph);
        this.body = body;
        this.unitToPointsTo = new HashMap<>();
        doAnalysis();
    }
    
    @Override
    protected void flowThrough(Map<Value, Set<String>> in, Unit unit, Map<Value, Set<String>> out) {
        // Copy in to out
        copy(in, out);
        
        Stmt stmt = (Stmt) unit;
        
        // Handle different statement types
        if (stmt instanceof AssignStmt) {
            AssignStmt assign = (AssignStmt) stmt;
            Value left = assign.getLeftOp();
            Value right = assign.getRightOp();
            
            if (right instanceof NewExpr) {
                // x = new T() => x points to a new object
                Set<String> pointsTo = new HashSet<>();
                String allocSite = "O" + unit.getJavaSourceStartLineNumber();
                pointsTo.add(allocSite);
                out.put(left, pointsTo);
                
            } else if (right instanceof Local) {
                // x = y => x points to what y points to
                Set<String> pointsTo = out.getOrDefault(right, new HashSet<>());
                out.put(left, new HashSet<>(pointsTo));
                
            } else if (right instanceof InstanceFieldRef) {
                // x = o.f => weak update (may point to many things)
                InstanceFieldRef fieldRef = (InstanceFieldRef) right;
                Value base = fieldRef.getBase();
                
                // For simplicity, create unknown points-to set
                Set<String> pointsTo = new HashSet<>();
                pointsTo.add("UNKNOWN");
                out.put(left, pointsTo);
            }
        }
        
        // Store the points-to information for this unit
        unitToPointsTo.put(unit, new HashMap<>(out));
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
    protected void merge(Map<Value, Set<String>> in1, Map<Value, Set<String>> in2, Map<Value, Set<String>> out) {
        out.clear();
        
        // Union of all variables
        Set<Value> allVars = new HashSet<>();
        allVars.addAll(in1.keySet());
        allVars.addAll(in2.keySet());
        
        for (Value var : allVars) {
            Set<String> pointsTo = new HashSet<>();
            if (in1.containsKey(var)) {
                pointsTo.addAll(in1.get(var));
            }
            if (in2.containsKey(var)) {
                pointsTo.addAll(in2.get(var));
            }
            out.put(var, pointsTo);
        }
    }
    
    @Override
    protected void copy(Map<Value, Set<String>> source, Map<Value, Set<String>> dest) {
        dest.clear();
        for (Map.Entry<Value, Set<String>> entry : source.entrySet()) {
            dest.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
    }
    
    public Set<String> getPointsToSet(Value var, Unit unit) {
        Map<Value, Set<String>> pointsTo = unitToPointsTo.get(unit);
        if (pointsTo != null && pointsTo.containsKey(var)) {
            return pointsTo.get(var);
        }
        return Collections.emptySet();
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
        
        // Method calls kill all loads (conservative)
        if (stmt.containsInvokeExpr()) {
            out.clear();
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