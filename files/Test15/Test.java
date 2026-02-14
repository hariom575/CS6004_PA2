class Test {
    Test f;
    Test g;
    Test h;

    void foo() { }

    public static void main(String[] args) {
        Test o = new Test();

        Test t1 = o.f;
        Test t2 = t1.g;
        Test t3 = t2.h;

        o.foo();   // must kill entire reachable graph

        Test t4 = t2.h;   // NOT redundant
    }
}
