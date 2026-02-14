class Node {
    Node f;
    Node g;

    void update() {
        this.f = new Node();
    }
}

public class Test {

    public static void main(String[] args) {

        Node[] arr = new Node[3];

        for (int i = 0; i < 3; i++) {
            arr[i] = new Node();
            arr[i].f = new Node();
            arr[i].f.g = new Node();
        }

        Node x = null;
        Node y = null;

        // REDUNDANT LOAD (same access twice)
        for (int i = 0; i < 3; i++) {
            x = arr[i].f.g;
            y = arr[i].f.g;   // should be redundant
        }

        // VIRTUAL CALL (invalidate)
        arr[0].update();

        // NOT REDUNDANT after invalidation
        Node z = arr[0].f.g;
    }
}
