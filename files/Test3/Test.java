public class Test {
    int f1;
    int f2;
    
    void testBranches(boolean cond) {
        Test a = new Test();
        a.f1 = 10;
        int x;
        
        if (cond) {
            x = a.f1;  // Load in then branch
        } else {
            x = a.f2;  // Load in else branch
        }
        
        int y = a.f1;  // redundant (loaded on both paths)
        
        // This should NOT be redundant (different field)
        int z = a.f2;
    }
}
