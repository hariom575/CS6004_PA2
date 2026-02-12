public class Test {
    int f1;
    int f2;
    
    void testWriteKills() {
        Test a = new Test();
        a.f1 = 10;
        
        int x = a.f1;  // First load
        int y = a.f1;  // redundant
        
        a.f1 = 20;     // Write kills previous loads
        
        int z = a.f1;  // NOT redundant (write killed it)
        int w = a.f1;  // redundant (same as z)
    }
}
