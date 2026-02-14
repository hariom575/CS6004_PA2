class Test {
    Test f1;
    Test f2;

    public static void main(String[] args) {
        Test o = new Test();

        Test x = o.f1;
        Test y = o.f2;   // NOT redundant
    }
}
