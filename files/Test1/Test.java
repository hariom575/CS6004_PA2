package Test1;
class Node{
    Node f1;
    Node f2;
    Node g;
    Node(){}
}

public class Test{
    public static void main(String[] args){
        Node a = new Node();  // O11
        a.f1 = new Node();     // O12
        Node b = new Node();   // O13
        b.f1 = new Node(); // O14
        a.f2 = new Node();  // O15
        Node c = a.f1;
        a.f2 = a.f1;   // redundant - line 17
        b.f1 = a.f2;   // redundant - line 18
    }
}
