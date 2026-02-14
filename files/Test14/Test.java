class Test {
    Test f;
    Test g;

    void foo() { }

    public static void main(String[] args) {
        Test o1 = new Test();
        Test o2 = o1;

        Test t1 = o1.f;
        Test t2 = t1.g;

        o2.foo();   // alias call → kill

        Test t3 = t1.g;   // NOT redundant
    }
}
