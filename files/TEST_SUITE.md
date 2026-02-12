# Comprehensive Test Suite for Redundant Load Analysis

## Test Suite Overview

This test suite contains 10 test cases covering various scenarios of redundant field loads:

1. **TestCase1**: Basic field-to-field assignments (from specification)
2. **TestCase2**: Method calls and primitive fields (from specification)
3. **TestCase3**: Control flow with branches
4. **TestCase4**: Write invalidation
5. **TestCase5**: Multiple objects and aliasing
6. **TestCase6**: Chained field accesses
7. **TestCase7**: Loop scenarios
8. **TestCase8**: Mixed field types
9. **TestCase9**: Method parameters and calls
10. **TestCase10**: Edge cases and complex scenarios

---

## Test Case 1: Basic Field-to-Field Assignments

**File**: `TestCase1.java`

**Purpose**: Test the fundamental pattern from the specification where field values are loaded and immediately assigned to other fields.

**Source Code**:
```java
Node a = new Node();  // O10
a.f1 = new Node();    // O11
Node b = new Node();  // O12
b.f1 = new Node();    // O13
a.f2 = new Node();    // O14
Node c = a.f1;        // Line 16
a.f2 = a.f1;          // Line 17: redundant
b.f1 = a.f2;          // Line 18: redundant
```

**Expected Redundancies**:
- Line 17: Loading `a.f1` is redundant (already loaded at line 16)
- Line 18: Loading `a.f2` is redundant (already loaded at line 17)

**Key Concepts Tested**:
- Field-to-field assignments
- Tracking loads across statements
- Destination field reporting

**Expected Output**:
```
Test: main
17: $r0.<Node: Node f2> $r5;
18: $r2.<Node: Node f1> $r6;
```

---

## Test Case 2: Method Calls and Primitive Fields

**File**: `TestCase2.java`

**Purpose**: Test how method calls invalidate field loads and test aliasing with primitive fields.

**Source Code**:
```java
// main():
Test a = new Test();
a.f1 = 10;
int b = a.f1;
int c = a.f1;    // redundant
a.foo();
int d = a.f1;    // NOT redundant (foo() killed it)

// foo():
Test o1 = new Test();
o1.f1 = 20;
Test o2 = o1;
x = o1.f1;
int y = o2.f1;   // redundant (o2 aliases o1)
```

**Expected Redundancies**:
- `main()`: Line 8 - `c = a.f1` is redundant
- `foo()`: Line 19 - `y = o2.f1` is redundant (aliasing)

**Key Concepts Tested**:
- Primitive field loads
- Method calls kill all loads
- Aliasing through assignment
- Points-to analysis for aliasing detection

**Expected Output**:
```
Test: main
8: $r0.<Test: int f1> i1;

Test: foo
19: $r0.<Test: int f1> i1;
```

---

## Test Case 3: Control Flow with Branches

**File**: `TestCase3.java`

**Purpose**: Test intersection of available loads at control flow merge points.

**Source Code**:
```java
if (cond) {
    x = a.f1;  // Load in then
} else {
    x = a.f1;  // Load in else
}
int y = a.f1;  // redundant (both paths)
int z = a.f2;  // NOT redundant (different field)
```

**Expected Redundancies**:
- Line after if-else: `y = a.f1` is redundant (loaded on BOTH paths)

**Key Concepts Tested**:
- Control flow merge (intersection)
- Must-alias analysis (available on ALL paths)
- Field sensitivity (f1 vs f2)

**Expected Output**:
```
Test: testBranches
[line]: $r0.<Test: int f1> i0;
```

---

## Test Case 4: Write Invalidation

**File**: `TestCase4.java`

**Purpose**: Test that writes to fields properly invalidate previous loads.

**Source Code**:
```java
int x = a.f1;  // First load
int y = a.f1;  // redundant

a.f1 = 20;     // WRITE kills loads

int z = a.f1;  // NOT redundant
int w = a.f1;  // redundant (same as z)
```

**Expected Redundancies**:
- Line with `y = a.f1`: redundant
- Line with `w = a.f1`: redundant (after new baseline)

**Key Concepts Tested**:
- Write operations kill loads
- Kill set computation
- May-alias for writes

**Expected Output**:
```
Test: testWriteKills
[line_y]: $r0.<Test: int f1> i1;
[line_w]: $r0.<Test: int f1> i3;
```

---

## Test Case 5: Multiple Objects and Aliasing

**File**: `TestCase5.java`

**Purpose**: Test that loads from different objects are distinguished.

**Source Code**:
```java
Test obj1 = new Test();
Test obj2 = new Test();

obj1.value = 100;
obj2.value = 200;

int a = obj1.value;  // First from obj1
int b = obj1.value;  // redundant (same object)

int c = obj2.value;  // NOT redundant (different object)
int d = obj2.value;  // redundant (same as c)

Test alias = obj1;
int e = alias.value; // redundant (alias -> obj1)
```

**Expected Redundancies**:
- `b = obj1.value`: redundant
- `d = obj2.value`: redundant
- `e = alias.value`: redundant (aliasing)

**Key Concepts Tested**:
- Multiple allocation sites
- Object sensitivity
- Aliasing detection via points-to analysis

---

## Test Case 6: Chained Field Accesses

**File**: `TestCase6.java`

**Purpose**: Test field access chains like `a.f.g`.

**Source Code**:
```java
Node head = new Node();
head.next = new Node();
head.next.next = new Node();

Node temp1 = head.next;
Node temp2 = head.next;       // redundant
Node temp3 = head.next.next;  // NOT redundant (different path)
Node temp4 = head.next.next;  // redundant
```

**Expected Redundancies**:
- `temp2 = head.next`: redundant
- `temp4 = head.next.next`: redundant

**Key Concepts Tested**:
- Chained field references
- Field path sensitivity
- Heap model for nested fields

---

## Test Case 7: Loop Scenarios

**File**: `TestCase7.java`

**Purpose**: Test loads in loops and repeated loads.

**Source Code**:
```java
void testSimpleRepeat() {
    Test obj = new Test();
    obj.counter = 10;
    
    int a = obj.counter;
    int b = obj.counter;  // redundant
    int c = obj.counter;  // redundant
    int d = obj.counter;  // redundant
}
```

**Expected Redundancies**:
- All loads after the first are redundant

**Key Concepts Tested**:
- Sequential redundancy
- Loop-invariant loads (bonus)

---

## Test Case 8: Mixed Field Types

**File**: `TestCase8.java`

**Purpose**: Test field sensitivity with different field types.

**Source Code**:
```java
int x = d.intField;
int y = d.intField;     // redundant

String s1 = d.strField;
String s2 = d.strField; // redundant

Data o1 = d.objField;
Data o2 = d.objField;   // redundant

d.intField = 100;       // Write kills intField loads
int z = d.intField;     // NOT redundant

String s3 = d.strField; // redundant (no write to strField)
```

**Expected Redundancies**:
- `y = d.intField`: redundant
- `s2 = d.strField`: redundant
- `o2 = d.objField`: redundant
- `s3 = d.strField`: redundant (write to different field)

**Key Concepts Tested**:
- Field sensitivity (different fields)
- Type independence
- Precise kill sets (only kill matching field)

---

## Test Case 9: Method Parameters and Calls

**File**: `TestCase9.java`

**Purpose**: Test intraprocedural analysis with method boundaries.

**Source Code**:
```java
void caller() {
    int x = obj.value;
    int y = obj.value;  // redundant
    helper(obj);        // Call kills all
    int z = obj.value;  // NOT redundant
}

void helper(Test param) {
    int a = param.value;
    int b = param.value;  // redundant
}
```

**Expected Redundancies**:
- In `caller()`: `y = obj.value` redundant
- In `helper()`: `b = param.value` redundant

**Key Concepts Tested**:
- Intraprocedural analysis (each method separate)
- Method calls kill all loads
- Conservative analysis at call sites

---

## Test Case 10: Edge Cases and Complex Scenarios

**File**: `TestCase10.java`

**Purpose**: Test complex aliasing and self-references.

**Source Code**:
```java
void testSelfReference() {
    Container c = new Container();
    c.self = c;
    c.data = 10;
    
    int x = c.data;
    int y = c.self.data;  // redundant (c.self == c)
}

void testComplexAliasing() {
    Container c1 = new Container();
    Container c2 = new Container();
    Container c3;
    
    c1.data = 100;
    int x = c1.data;
    
    c3 = c1;
    int y = c3.data;  // redundant (c3 -> c1)
    
    c3 = c2;
    int z = c3.data;  // NOT redundant (c3 now -> c2)
}
```

**Expected Redundancies**:
- `y = c.self.data`: redundant (if field-sensitive points-to works correctly)
- `y = c3.data`: redundant (after c3 = c1)

**Key Concepts Tested**:
- Self-referential structures
- Aliasing updates
- Complex points-to scenarios

---

## How to Run Each Test Case

### Setup:
```bash
# Create test directory
mkdir Test1 Test2 Test3 Test4 Test5 Test6 Test7 Test8 Test9 Test10

# Copy test files
cp TestCase1.java Test1/Test.java
cp TestCase2.java Test2/Test.java
cp TestCase3.java Test3/Test.java
# ... and so on
```

### Compile:
```bash
cd Test1
javac -g Test.java
cd ..

cd Test2
javac -g Test.java
cd ..

# ... repeat for all
```

### Run Analysis:
```bash
javac -cp .:soot-4.6.0-jar-with-dependencies.jar PA2.java

java -cp .:soot-4.6.0-jar-with-dependencies.jar PA2 Test1
java -cp .:soot-4.6.0-jar-with-dependencies.jar PA2 Test2
java -cp .:soot-4.6.0-jar-with-dependencies.jar PA2 Test3
# ... and so on
```

---

## Test Coverage Summary

| Test Case | Primary Concept | Secondary Concepts |
|-----------|----------------|-------------------|
| 1 | Field-to-field assignments | Destination field reporting |
| 2 | Method calls, Aliasing | Primitive fields, Conservative analysis |
| 3 | Control flow | Merge points, Intersection |
| 4 | Write invalidation | Kill sets |
| 5 | Multiple objects | Object sensitivity |
| 6 | Chained fields | Heap modeling |
| 7 | Loops | Sequential redundancy |
| 8 | Field sensitivity | Type independence |
| 9 | Method boundaries | Intraprocedural limits |
| 10 | Edge cases | Complex aliasing |

---

## Expected Analysis Behavior

### What SHOULD be detected:
✓ Same field loaded twice without intervening write
✓ Field loaded after being assigned to another field
✓ Loads through aliases (obj1 and obj2 point to same object)
✓ Loads available on all control flow paths

### What should NOT be detected:
✗ Loads from different objects
✗ Loads of different fields
✗ Loads after a write to the same field
✗ Loads after a method call
✗ Loads available on only some paths (not intersection)

---

## Debugging Tips

If a test case doesn't produce expected output:

1. **Check Jimple IR**:
   ```java
   Options.v().set_output_format(Options.output_format_jimple);
   ```

2. **Add debug output** in analysis:
   ```java
   System.err.println("Analyzing: " + stmt);
   System.err.println("Available: " + availableBefore);
   ```

3. **Verify line numbers**:
   - Ensure compiled with `-g` flag
   - Line numbers may shift based on formatting

4. **Check points-to sets**:
   - Print what each variable points to
   - Verify heap contents

---

## Advanced Testing

Create your own test cases to verify:
- Polymorphism (if supported)
- Array field loads (extension)
- Static fields (extension)
- Multiple method invocations
- Recursive calls (interprocedural extension)

Happy Testing! 🚀
