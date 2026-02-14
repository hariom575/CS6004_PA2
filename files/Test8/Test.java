class Test {
    Test f1;
    Test f2;

    public static void main(String[] args) {
        Test o = new Test();

        Test x = o.f1;

        o.f2 = new Test();   // different field

        Test y = o.f1;       // REDUNDANT
    }
}
