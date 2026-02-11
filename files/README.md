# Redundant Field Load Detection - Static Analysis

## Overview
This project implements a forward intraprocedural data flow analysis to detect redundant field loads in Java programs using the Soot framework.

## Problem Statement
A field load `x = o.f` is **redundant** if:
1. The same field `(o, f)` has been loaded into a variable on all paths leading to this point
2. There has been no write to `o.f` since that earlier load
3. The object `o` points to the same allocation sites as the earlier load

## Solution Architecture

### 1. **Points-to Analysis**
- **Type**: Flow-sensitive, field-sensitive, intraprocedural
- **Purpose**: Track which abstract objects each variable may point to
- **Abstraction**: Uses allocation site as abstract object names (e.g., "O10" for line 10)

**Key Operations**:
- `x = new T()` → x points to new allocation site
- `x = y` → x points to same objects as y
- `x = o.f` → x points to unknown (weak update due to field sensitivity)

### 2. **Available Loads Analysis**
- **Type**: Forward data flow analysis
- **Domain**: Set of field loads `{(base, field, target)}`
- **Purpose**: Track which field loads are available (can be reused) at each program point

**Transfer Functions**:
- **Field Read** `x = o.f`: Generate new load `(o, f, x)`
- **Field Write** `o.f = v`: Kill all loads `(o', f)` where `o'` may alias with `o`
- **Method Call**: Kill all loads (conservative - may modify any field)
- **Assignment** `x = y`: No effect on field loads

**Meet Operation**: Intersection (loads available on ALL paths)

### 3. **Redundancy Detection**
For each field load `x = o.f` at line L:
1. Get available loads before this statement
2. Get points-to set for base object `o`
3. Check if any available load `(o', f, x')` exists where:
   - `o'` may alias with `o` (points-to sets overlap)
   - Field `f` matches
4. If found → this load is redundant and can use `x'` instead

## Algorithm Details

### Points-to Analysis (Intraprocedural)
```
Domain: Map<Variable, Set<AllocationSite>>

flowThrough(in, stmt, out):
    out = copy(in)
    
    if stmt is "x = new T()":
        out[x] = {allocationSite(stmt)}
    
    elif stmt is "x = y":
        out[x] = in[y]
    
    elif stmt is "x = o.f":
        out[x] = {UNKNOWN}  // weak update
    
    // Handle other cases...

merge(in1, in2, out):
    out = {}
    for each variable v:
        out[v] = in1[v] ∪ in2[v]  // Union for may-analysis
```

### Available Loads Analysis
```
Domain: Set<FieldLoad> where FieldLoad = (base, field, target)

flowThrough(in, stmt, out):
    out = copy(in)
    
    if stmt is "x = o.f":
        // Generate new load
        out = out ∪ {(o, f, x)}
    
    elif stmt is "o.f = v":
        // Kill loads that may alias
        for each (o', f', x') in out:
            if mayAlias(o, o') and f == f':
                out = out \ {(o', f', x')}
    
    elif stmt contains method call:
        out = {}  // Kill all (conservative)

merge(in1, in2, out):
    out = in1 ∩ in2  // Intersection: available on ALL paths
```

## Example Walkthrough

### Example 1: Basic Redundancy
```java
Node a = new Node();  // O10
a.f1 = new Node();    // O11
Node c = a.f1;        // Line 16: Load a.f1 into c
a.f2 = a.f1;          // Line 17: Load a.f1 into temp (REDUNDANT!)
```

**Analysis**:
1. After line 16: Available loads = {(a, f1, c)}
2. At line 17: Loading `a.f1` again
3. Check: Is `(a, f1, _)` available? YES → Load is redundant!
4. Output: Can replace with variable `c`

### Example 2: Killed by Write
```java
Test o1 = new Test();
o1.f1 = 10;
int b = o1.f1;        // First load
o1.f1 = 20;           // WRITE kills the load
int c = o1.f1;        // NOT redundant (different value)
```

### Example 3: Killed by Method Call
```java
int b = a.f1;         // Load
a.foo();              // Method call - may modify a.f1
int d = a.f1;         // NOT redundant (foo() may have changed it)
```

### Example 4: Must-Alias Redundancy
```java
Test o1 = new Test();
o1.f1 = 20;
Test o2 = o1;         // o2 aliases o1
x = o1.f1;            // First load
y = o2.f1;            // REDUNDANT (o2 aliases o1)
```

## Output Format
```
ClassName: methodName
LineNumber: JimpleStatement TargetVariable;
```

**Example**:
```
Test: main
16: $r0.<Node: Node f1> r5;
17: $r0.<Node: Node f2> r5;

Test: foo
17: $r0.<Test: int f1> i0;
```

## Key Design Decisions

### 1. **Intraprocedural Analysis**
- Only analyzes within a single method
- Conservative at method call boundaries (kills all loads)
- No interprocedural propagation

### 2. **Flow-Sensitive Points-to**
- Different points in the program may have different points-to information
- More precise than flow-insensitive analysis

### 3. **Field-Sensitive**
- Distinguishes between different fields (f1 vs f2)
- More precise than field-insensitive analysis

### 4. **Must-Alias Check**
- Uses points-to sets to check aliasing
- Conservative: If points-to sets overlap, assume may-alias

### 5. **Meet Operation**
- Uses INTERSECTION for available loads
- A load is only available if it's available on ALL paths
- Ensures soundness: we only report true redundancies

## Limitations & Extensions

### Current Limitations:
1. **Intraprocedural only** - Doesn't track across method boundaries
2. **No array handling** - Only handles field accesses
3. **Conservative at calls** - Kills all loads at method invocations
4. **Simple points-to** - Could be more precise with context-sensitivity

### Possible Extensions:
1. **Interprocedural analysis** - Track across method calls
2. **Array analysis** - Handle `a[i]` accesses
3. **Call graph analysis** - More precise kill sets at calls
4. **Context-sensitive points-to** - Different contexts for recursive calls
5. **Strong updates** - When definitely only one object, can do strong update

## Compilation and Execution

### 1. Compile Test Cases
```bash
javac Test.java
```

### 2. Compile Analysis
```bash
javac -cp .:soot-4.6.0-jar-with-dependencies.jar PA2.java
```

### 3. Run Analysis
```bash
java -cp .:soot-4.6.0-jar-with-dependencies.jar PA2 Test1
```

## Implementation Details

### Class Structure:
- **PA2**: Main driver class
  - Configures Soot
  - Iterates through classes/methods
  - Collects and prints results

- **PointsToAnalysis**: Flow-sensitive points-to analysis
  - Extends ForwardFlowAnalysis
  - Tracks variable → allocation site mappings

- **AvailableLoadsAnalysis**: Available loads analysis
  - Extends ForwardFlowAnalysis
  - Tracks available field loads
  - Uses points-to info for kill sets

- **FieldLoad**: Represents a field load operation
  - Stores (base, field, target)
  - Used in available loads set

### Key Methods:
- `analyzeMethod()`: Main analysis driver for a method
- `flowThrough()`: Transfer function for data flow
- `merge()`: Meet operation for joining flows
- `getPointsToSet()`: Query points-to information

## Testing

The solution handles the provided test cases:

### Test 1: Node Field Accesses
- Detects redundant loads of `a.f1` 
- Properly tracks through assignments

### Test 2: Primitive Fields with Method Calls
- Detects redundant load in `foo()`
- Kills loads across method calls in `main()`

## References
- Soot Framework: https://soot-oss.github.io/soot/
- Data Flow Analysis: Principles, Techniques, and Tools (Dragon Book)
- Points-to Analysis: Andersen's and Steensgaard's algorithms
