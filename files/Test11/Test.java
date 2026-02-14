class Test {
    Test f;
    Test g;

    public static void main(String[] args) {
        Test o = new Test();

        Test t1 = o.f;
        Test t2 = t1.g;
        Test t3 = t1.g;   // REDUNDANT
    }
}
