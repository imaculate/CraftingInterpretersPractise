#include <stdio.h>
#include <stdlib.h>

struct Node
{
    char data;
    Node* prev;
    Node* next;
};

/* Given a reference (pointer to pointer) to the head of a
   list and an int, inserts a new node on the front of the
   list. */
void pushToFront(struct Node** head_ref, char new_data)
{
    Node* n = new Node();
    n->data = new_data;
    
    n->next = (*head_ref);
    n->prev = NULL;
    
    if ((*head_ref) != NULL)
        (*head_ref)->prev = n;
    
    (*head_ref) = n;
}

void printList(Node* node)
{
    Node* r = node;
    while (r != NULL)
    {
        printf(" %c ", r->data);
        r = r->next;
    }
}

int main() {
    printf("Hello World!\n");
    Node* head = NULL;
    pushToFront(&head, 'r');
    pushToFront(&head, 'a');
    pushToFront(&head, 't');
    pushToFront(&head, 's');
    printList(head);
}