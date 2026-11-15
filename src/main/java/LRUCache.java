import java.util.HashMap;
import java.util.Map;

/**
 * An LRU-style cache that evicts the least recently used entry to keep memory bounded.
 * It uses a hash map for O(1) access plus a doubly linked list to track recency.
 */
public class LRUCache {
    private final Map<String, Node> map = new HashMap<>();
    private final Node head = new Node("HEAD");
    private final Node tail = new Node("TAIL");
    private final int capacity;

    public LRUCache(int capacity) {
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    public void put(String key, String value) {
        // If the key already exists, remove the stale entry before inserting the newer one.
        if (map.containsKey(key)) {
            remove(key);
        }
        // Capacity is enforced before adding the new item so the least recently used value is evicted.
        if (map.size() >= capacity) {
            evictLeastRecentlyUsed();
        }

        // Insert the new node at the front because most recently used items should stay closest to head.
        Node node = new Node(key, value);
        insertAtFront(node);
        map.put(key, node);
    }

    public String get(String key) {
        // Lookup is O(1) through the map and we update recency because the item was just used.
        Node node = map.get(key);
        if (node == null) {
            return null;
        }
        moveToFront(node);
        return node.value;
    }

    public boolean contains(String key) {
        return map.containsKey(key);
    }

    public int size() {
        return map.size();
    }

    private void evictLeastRecentlyUsed() {
        // The tail is the least recently used element in the linked list ordering.
        Node last = tail.prev;
        if (last == head) {
            return;
        }
        remove(last.key);
    }

    private void insertAtFront(Node node) {
        // Link the node between the head sentinel and the current most-recent item.
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void moveToFront(Node node) {
        removeNode(node);
        insertAtFront(node);
    }

    private void remove(String key) {
        // Remove from the map and splice the node out of the doubly linked list.
        Node node = map.remove(key);
        if (node != null) {
            removeNode(node);
        }
    }

    private void removeNode(Node node) {
        Node prev = node.prev;
        Node next = node.next;
        prev.next = next;
        next.prev = prev;
        node.prev = null;
        node.next = null;
    }

    private static final class Node {
        private final String key;
        private final String value;
        private Node prev;
        private Node next;

        private Node(String key) {
            this.key = key;
            this.value = null;
        }

        private Node(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
