class Test {
    Test f;
    Test g;

    void foo() { }

    public static void main(String[] args) {
        Test o = new Test();

        Test t1 = o.f;
        Test t2 = t1.g;

        o.foo();   // should invalidate o and its chain

        Test t3 = t1.g;   // NOT redundant
    }
}
