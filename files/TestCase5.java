public class Test {
    int value;
    
    void testAliasing() {
        Test obj1 = new Test();
        Test obj2 = new Test();
        
        obj1.value = 100;
        obj2.value = 200;
        
        int a = obj1.value;  // First load from obj1
        int b = obj1.value;  // redundant (same object, same field)
        
        int c = obj2.value;  // NOT redundant (different object)
        int d = obj2.value;  // redundant (same as c)
        
        // Aliasing test
        Test alias = obj1;
        int e = alias.value;  // redundant (alias points to obj1)
    }
}
