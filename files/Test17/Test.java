class Node {
    Node next;

    void mutate() {
        this.next = new Node();
    }
}

public class Test {

    public static void main(String[] args) {

        Node[] list = new Node[2];

        for (int i = 0; i < 2; i++) {
            list[i] = new Node();
            list[i].next = new Node();
        }

        Node a = null;
        Node b = null;

        // Nested loop redundant case
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 1; j++) {

                a = list[i].next;
                b = list[i].next;   // should be redundant
            }
        }

        // Virtual call inside loop
        for (int i = 0; i < 2; i++) {
            list[i].mutate();
        }

        // After mutation → NOT redundant
        Node c = list[0].next;
        Node d = list[0].next;  // redundant again (no change between c & d)
    }
}
