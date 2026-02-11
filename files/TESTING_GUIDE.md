# Testing Guide for Redundant Load Analysis

## Setup Instructions

### Prerequisites
1. Java JDK 8 or higher
2. Soot framework JAR file: `soot-4.6.0-jar-with-dependencies.jar`

### Directory Structure
```
project/
├── PA2.java                          # Main analysis code
├── soot-4.6.0-jar-with-dependencies.jar
├── Test1/                            # Test case 1 folder
│   └── Test.java
├── Test2/                            # Test case 2 folder
│   └── Test.java
└── README.md
```

## Compilation Steps

### Step 1: Compile Test Cases
For each test folder:
```bash
# Test 1
cd Test1
javac Test.java
cd ..

# Test 2
cd Test2
javac Test.java
cd ..
```

### Step 2: Compile Analysis Tool
```bash
javac -cp .:soot-4.6.0-jar-with-dependencies.jar PA2.java
```

**Note for Windows:**
```bash
javac -cp ".;soot-4.6.0-jar-with-dependencies.jar" PA2.java
```

## Running the Analysis

### Basic Usage
```bash
java -cp .:soot-4.6.0-jar-with-dependencies.jar PA2 Test1
```

**Windows:**
```bash
java -cp ".;soot-4.6.0-jar-with-dependencies.jar" PA2 Test1
```

### Expected Output Format
```
ClassName: methodName
LineNumber: FieldLoadStatement TargetVariable;
```

## Test Cases

### Test Case 1: Basic Redundant Loads (Node Example)

**Source Code (Test1/Test.java):**
```java
class Node{
    Node f1;
    Node f2;
    Node g;
    Node(){}
}

public class Test{
    public static void main(String[] args){
        Node a = new Node();   // Line 10
        a.f1 = new Node();     // Line 11
        Node b = new Node();   // Line 12
        b.f1 = new Node();     // Line 13
        a.f2 = new Node();     // Line 14
        Node c = a.f1;         // Line 16: Load a.f1
        a.f2 = a.f1;           // Line 17: Load a.f1 (REDUNDANT)
        b.f1 = a.f2;           // Line 18: Load a.f2 (REDUNDANT)
    }
}
```

**Expected Output:**
```
Test: main
17: $r0.<Node: Node f1> $r6;
18: $r0.<Node: Node f2> $r7;
```

**Explanation:**
- Line 17: `a.f1` was already loaded at line 16 into variable `c` (jimple: $r5)
- Line 18: `a.f2` was already loaded at line 17 into temporary (jimple: $r6)

---

### Test Case 2: Method Calls and Aliasing

**Source Code (Test2/Test.java):**
```java
public class Test {
    int f1;
    
    public static void main(String[] args){
        Test a = new Test();
        a.f1 = 10;
        int b = a.f1;          // Line 7: First load
        int c = a.f1;          // Line 8: REDUNDANT
        a.foo();               // Line 9: Method call kills loads
        int d = a.f1;          // Line 10: NOT redundant
    }
    
    void foo(){
        Test o1 = new Test();
        int x;
        o1.f1 = 20;
        Test o2 = o1;          // o2 aliases o1
        x = o1.f1;             // Line 18: First load
        int y = o2.f1;         // Line 19: REDUNDANT (aliasing)
    }
}
```

**Expected Output:**
```
Test: main
8: $r0.<Test: int f1> i1;

Test: foo
19: $r0.<Test: int f1> i1;
```

**Explanation:**
- In `main`:
  - Line 8: `a.f1` already loaded at line 7
  - Line 10: NOT redundant because foo() call killed the load
- In `foo`:
  - Line 19: `o2.f1` redundant because o2 aliases o1, and `o1.f1` loaded at line 18

---

### Test Case 3: Control Flow

**Source Code (Test3/Test.java):**
```java
public class Test {
    int f1;
    
    void test(boolean cond) {
        Test a = new Test();
        a.f1 = 10;
        int x;
        
        if (cond) {
            x = a.f1;          // Line 10: Load in then
        } else {
            x = a.f1;          // Line 12: Load in else
        }
        
        int y = a.f1;          // Line 15: REDUNDANT (both paths)
    }
}
```

**Expected Output:**
```
Test: test
15: $r0.<Test: int f1> i1;
```

**Explanation:**
- Load at line 15 is redundant because `a.f1` was loaded on BOTH branches
- The intersection of available loads includes this load

---

### Test Case 4: Write Invalidation

**Source Code (Test4/Test.java):**
```java
public class Test {
    int f1;
    
    void test() {
        Test a = new Test();
        a.f1 = 10;
        int x = a.f1;          // Line 8: First load
        int y = a.f1;          // Line 9: REDUNDANT
        a.f1 = 20;             // Line 10: Write kills load
        int z = a.f1;          // Line 11: NOT redundant
    }
}
```

**Expected Output:**
```
Test: test
9: $r0.<Test: int f1> i1;
```

**Explanation:**
- Line 9: Redundant (same as line 8)
- Line 11: NOT redundant (write at line 10 invalidated the load)

---

## Debugging Tips

### 1. Enable Jimple Output
To see the Jimple intermediate representation:
```java
Options.v().set_output_format(Options.output_format_jimple);
```

### 2. Print Analysis Results
Add debug output in the analysis:
```java
System.err.println("Checking unit: " + stmt);
System.err.println("Available loads: " + availableBefore);
System.err.println("Points-to: " + basePointsTo);
```

### 3. Check Line Numbers
If line numbers are -1:
- Ensure source files are compiled with debug info: `javac -g Test.java`
- Check that `Options.v().set_keep_line_number(true)` is set

### 4. Verify Jimple Generation
Look at generated Jimple in `sootOutput/` directory (if enabled)

---

## Common Issues and Solutions

### Issue 1: "Class not found"
**Solution:** Ensure test folder is in classpath:
```bash
-Dsoot.class.path=.:./Test1
```

### Issue 2: No output produced
**Possible causes:**
1. No redundant loads in the code
2. Constructor methods being analyzed (they're skipped)
3. Line numbers not available (compile with `-g`)

### Issue 3: Incorrect redundancies detected
**Check:**
1. Points-to analysis is correctly tracking objects
2. Writes are properly killing loads
3. Method calls are killing all loads

### Issue 4: Missing redundancies
**Check:**
1. Merge operation uses intersection (not union)
2. Aliasing check is using points-to overlap
3. Field comparison is exact match

---

## Advanced Testing

### Custom Test Cases

**Test: Array fields (not supported)**
```java
class Test {
    int[] arr;
    void test() {
        arr[0] = 10;
        int x = arr[0];
        int y = arr[0];  // Should detect but may not (array sensitivity)
    }
}
```

**Test: Static fields**
```java
class Test {
    static int sf;
    void test() {
        Test.sf = 10;
        int x = Test.sf;
        int y = Test.sf;  // Should be redundant
    }
}
```

**Test: Loop invariants**
```java
void test() {
    Test a = new Test();
    a.f1 = 10;
    for (int i = 0; i < 10; i++) {
        int x = a.f1;  // Loop invariant (could be hoisted)
    }
}
```

---

## Performance Considerations

### Analysis Complexity
- **Points-to:** O(n) per method (single pass)
- **Available loads:** O(n × d) where d is average # of loads
- **Overall:** Linear in method size for typical programs

### Scalability
- Intraprocedural: Scales well to large methods
- Memory: Proportional to number of units × domain size
- Bottleneck: CFG construction and Jimple generation

---

## Validation Checklist

✅ Detects redundant loads after first access
✅ Handles control flow (intersection at merge)
✅ Kills loads on field writes
✅ Kills all loads on method calls
✅ Handles aliasing (points-to analysis)
✅ Sorts output by line number
✅ Skips constructors
✅ Only prints methods with redundancies

---

## Further Enhancements

Possible extensions to the analysis:

1. **Interprocedural analysis**
   - Track across method boundaries
   - More precise at call sites

2. **Array support**
   - Handle array element loads
   - Array-sensitive analysis

3. **Strong updates**
   - When definitely one object, do strong update
   - More precise kill sets

4. **Loop optimization**
   - Detect loop-invariant loads
   - Support load hoisting

5. **Partial redundancy elimination**
   - Insert loads on paths that don't have them
   - More aggressive optimization

---

## References

1. Soot Documentation: https://soot-oss.github.io/soot/
2. Data Flow Analysis: Dragon Book, Chapter 9
3. Points-to Analysis: Andersen's algorithm
4. Available Expressions: Classic compiler optimization
