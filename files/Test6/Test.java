class Test {
    Test f1;

    public static void main(String[] args) {
        Test o = new Test();

        Test x = o.f1;

        o = new Test();   // new object

        Test y = o.f1;    // NOT redundant
    }
}
