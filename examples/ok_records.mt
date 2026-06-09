// ok_records.mt — structs (records), field access, and a recursive struct.  Should SUCCEED.

struct Point { int x; int y; };
struct Node  { int data; Node* next; };   // recursive: the Node* field refers to Node

int usePoint() {
    Point p;
    p.x = 3;                 // field is int
    p.y = 4;
    return p.x + p.y;        // int
}

int listHead(Node* n) {
    return (*n).data;        // dereference the pointer, then read the int field
}
