class Test {
    Test f1;

    public static void main(String[] args) {
        Test o1 = new Test();
        Test o2 = o1;
        Test o3 = o2;

        Test x = o1.f1;
        Test y = o3.f1;   // REDUNDANT
    }
}
