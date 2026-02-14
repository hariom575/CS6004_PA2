class Test {
    Test f1;

    void bar(Test t) {
        t.f1 = new Test();
    }

    public static void main(String[] args) {
        Test o = new Test();

        Test x = o.f1;

        o.bar(o);   // kills f1

        Test y = o.f1;   // NOT redundant
    }
}
