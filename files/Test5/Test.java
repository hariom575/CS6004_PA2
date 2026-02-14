class Test {
    Test f1;

    public static void main(String[] args) {
        Test o;

        if (args.length > 0)
            o = new Test();
        else
            o = new Test();

        Test x = o.f1;

        o.f1 = new Test();   // weak update

        Test y = o.f1;       // NOT redundant
    }
}
