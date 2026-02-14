class Test {
    Test f;
    Test g;

    void foo() { }

    public static void main(String[] args) {
        Test o1 = new Test();
        Test o2 = new Test();

        Test t1 = o1.f;
        Test t2 = t1.g;

        o2.foo();   // should NOT kill o1 chain

        Test t3 = t1.g;   // REDUNDANT
    }
}
