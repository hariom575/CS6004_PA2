# Redundant Load Analysis - Visual Guide

## Data Flow Analysis Overview

```
                    ┌─────────────────────────┐
                    │   Points-to Analysis    │
                    │  (Which objects does    │
                    │   each variable point   │
                    │   to?)                  │
                    └───────────┬─────────────┘
                                │
                                ▼
                    ┌─────────────────────────┐
                    │ Available Loads Analysis│
                    │  (Which field loads are │
                    │   still valid?)         │
                    └───────────┬─────────────┘
                                │
                                ▼
                    ┌─────────────────────────┐
                    │  Redundancy Detection   │
                    │  (Is this load needed?) │
                    └─────────────────────────┘
```

## Example 1: Basic Redundant Load

### Source Code:
```java
Node a = new Node();  // Line 10
a.f1 = new Node();    // Line 11
Node c = a.f1;        // Line 16: First load
a.f2 = a.f1;          // Line 17: REDUNDANT load
```

### Jimple IR:
```
$r0 = new Node;           // L10
$r1 = new Node;           // L11
$r0.<Node: Node f1> = $r1;
$r5 = $r0.<Node: Node f1>;  // L16: Load a.f1 -> $r5
$r6 = $r0.<Node: Node f1>;  // L17: Load a.f1 -> $r6 (REDUNDANT!)
$r0.<Node: Node f2> = $r6;
```

### Analysis Flow:

**Step 1: Points-to Analysis**
```
After L10:  $r0 → {O10}
After L11:  $r0 → {O10}, $r1 → {O11}
After L16:  $r0 → {O10}, $r1 → {O11}, $r5 → {O11}
After L17:  $r0 → {O10}, $r1 → {O11}, $r5 → {O11}, $r6 → {O11}
```

**Step 2: Available Loads**
```
Before L16:  ∅
After L16:   {($r0, f1, $r5)}
Before L17:  {($r0, f1, $r5)}  ← Load is available!
After L17:   {($r0, f1, $r6)}  ← New load replaces old
```

**Step 3: Redundancy Check at L17**
```
Current load: $r6 = $r0.f1
Available:    ($r0, f1, $r5)

Check:
  ✓ Same base: $r0 == $r0
  ✓ Same field: f1 == f1
  ✓ Base points-to overlap: {O10} ∩ {O10} ≠ ∅

Result: REDUNDANT! Can use $r5 instead of loading again
```

**Output:**
```
Test: main
17: $r0.<Node: Node f1> $r6;
```

---

## Example 2: Load Killed by Write

### Source Code:
```java
Test o1 = new Test();
o1.f1 = 10;
int b = o1.f1;     // Line 6: First load
o1.f1 = 20;        // Line 7: WRITE kills the load
int c = o1.f1;     // Line 8: NOT redundant
```

### Jimple IR:
```
$r0 = new Test;                 // O5
$r0.<Test: int f1> = 10;
i0 = $r0.<Test: int f1>;        // L6: Load
$r0.<Test: int f1> = 20;        // L7: Write KILLS
i1 = $r0.<Test: int f1>;        // L8: Load (not redundant)
```

### Available Loads:
```
Before L6:  ∅
After L6:   {($r0, f1, i0)}
Before L7:  {($r0, f1, i0)}
After L7:   ∅                   ← Write killed the load!
Before L8:  ∅                   ← No available load
After L8:   {($r0, f1, i1)}
```

**At L8:** No available load → NOT redundant ✓

---

## Example 3: Load Killed by Method Call

### Source Code:
```java
int b = a.f1;      // First load
a.foo();           // Method call
int d = a.f1;      // NOT redundant (foo may modify f1)
```

### Available Loads:
```
Before load b:    ∅
After load b:     {(a, f1, b)}
Before foo():     {(a, f1, b)}
After foo():      ∅              ← Call kills all loads!
Before load d:    ∅
```

**Reason:** Conservative assumption - foo() might modify any field

---

## Example 4: Aliasing Detection

### Source Code:
```java
Test o1 = new Test();  // O12
o1.f1 = 20;
Test o2 = o1;          // o2 aliases o1
x = o1.f1;             // Line 15: First load
y = o2.f1;             // Line 17: REDUNDANT (same object!)
```

### Points-to Analysis:
```
After O12:  o1 → {O12}
After alias: o1 → {O12}, o2 → {O12}
```

### Redundancy Check at L17:
```
Current load: y = o2.f1
Available:    (o1, f1, x)

Check:
  ✗ Same base: o2 ≠ o1
  ✓ May alias: {O12} ∩ {O12} ≠ ∅    ← They point to same object!
  ✓ Same field: f1 == f1

Result: REDUNDANT! Both load from same object
```

---

## Transfer Functions Summary

### Points-to Transfer Function:
```
┌─────────────────────┬───────────────────────────┐
│ Statement           │ Effect on Points-to       │
├─────────────────────┼───────────────────────────┤
│ x = new T()         │ out[x] = {fresh}          │
│ x = y               │ out[x] = in[y]            │
│ x = o.f             │ out[x] = {unknown}        │
│ x = null            │ out[x] = ∅                │
└─────────────────────┴───────────────────────────┘
```

### Available Loads Transfer Function:
```
┌─────────────────────┬───────────────────────────┐
│ Statement           │ Effect on Available Loads │
├─────────────────────┼───────────────────────────┤
│ x = o.f             │ GEN: (o, f, x)            │
│ o.f = v             │ KILL: all (o', f, _)      │
│                     │   where o' may alias o    │
│ x = foo()           │ KILL: all loads           │
└─────────────────────┴───────────────────────────┘
```

### Meet Operation:
```
Available Loads uses INTERSECTION:

Path 1: {(a, f1, x), (b, f2, y)}
Path 2: {(a, f1, x), (c, f3, z)}
                ↓
Merged:  {(a, f1, x)}

Only loads available on ALL paths are kept!
```

---

## Control Flow Example

### Source Code:
```java
Node a = new Node();
a.f1 = new Node();
int x;

if (condition) {
    x = a.f1;        // Load in then branch
} else {
    x = a.f1;        // Load in else branch  
}

int y = a.f1;        // REDUNDANT (loaded on both paths)
```

### Control Flow Graph:
```
        [a.f1 = ...]
             │
             ▼
        [if condition]
         ╱        ╲
        ▼          ▼
   [x = a.f1]  [x = a.f1]
   Avail:{L}    Avail:{L}
         ╲        ╱
          ▼      ▼
         [MERGE]
      Avail: {L} ∩ {L} = {L}
             │
             ▼
        [y = a.f1]  ← REDUNDANT!
```

**Key Point:** Load is available on BOTH paths, so it's redundant after merge!

---

## Optimality Considerations

### What Makes a Load Redundant?
1. **Must be available on ALL paths** (not just some)
2. **No intervening writes** to that field
3. **No method calls** (conservative)
4. **Base objects must alias** (same allocation sites)

### What Makes Analysis Sound?
1. **Conservative at method calls** - kill all
2. **May-alias** for writes - kill if may alias
3. **Intersection at merge** - only report if available everywhere
4. **Weak updates** for field loads - don't assume unique

### Precision vs. Soundness Trade-off:
```
More Precise (fewer false negatives):
- Context-sensitive points-to
- Strong updates when possible
- Interprocedural analysis
- Side-effect analysis for calls

More Sound (no false positives):
- Conservative at calls (kill all)
- May-alias (kill if might alias)
- Intersection at merge
```

---

## Common Pitfalls

### ❌ Wrong: Using Union at Merge
```
Path 1: {(a, f1, x)}
Path 2: {(a, f2, y)}
Union:  {(a, f1, x), (a, f2, y)}  ← WRONG!

Problem: (a, f2, y) not available on path 1!
```

### ✓ Correct: Using Intersection
```
Path 1: {(a, f1, x)}
Path 2: {(a, f2, y)}
Intersection: ∅  ← Correct! Neither available on all paths
```

### ❌ Wrong: Exact Equality for Aliasing
```java
Test o1 = new Test();
Test o2 = o1;
x = o1.f1;
y = o2.f1;  // Should be redundant!

if (o1 == o2):  ← WRONG! Variables are different
    redundant
```

### ✓ Correct: Points-to Set Overlap
```
if (pointsTo(o1) ∩ pointsTo(o2) ≠ ∅):  ← Correct!
    may alias, check for redundancy
```

---

## Summary

The analysis works in three phases:
1. **Points-to**: Track what each variable points to
2. **Available loads**: Track which loads are still valid
3. **Redundancy**: Check if current load matches available load

Key insight: A load is redundant if we already loaded that exact field from that same object, and nothing has changed since!
