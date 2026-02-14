class Test {
    Test f1;

    public static void main(String[] args) {
        Test o = new Test();

        Test x = o.f1;

        if (args.length > 0) {
            o.f1 = new Test();
        }

        Test y = o.f1;   // NOT redundant
    }
}
