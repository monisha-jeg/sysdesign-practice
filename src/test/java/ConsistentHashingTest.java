import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import src.main.java.ConsistentHashing;

class ConsistentHashingTest {

    @Test
    void sameKeyAlwaysMapsToSameNode() {
        // A key should always resolve to the same node for a stable ring configuration.
        ConsistentHashing ring = new ConsistentHashing(128);
        ring.addNode("node-a");
        ring.addNode("node-b");
        ring.addNode("node-c");

        String key = "user:42";
        String first = ring.getNode(key);

        for (int i = 0; i < 20; i++) {
            assertEquals(first, ring.getNode(key));
        }
    }

    @Test
    void removingNodeMovesKeysOffRemovedNode() {
        ConsistentHashing ring = new ConsistentHashing(128);
        ring.addNode("node-a");
        ring.addNode("node-b");
        ring.addNode("node-c");

        String keyToMove = null;
        for (int i = 0; i < 1000; i++) {
            String key = "key-" + i;
            if (ring.getNode(key).equals("node-b")) {
                // We first pick a key that currently belongs to node-b.
                keyToMove = key;
                break;
            }
        }

        assertNotEquals(null, keyToMove, "Expected at least one key assigned to node-b");

        ring.removeNode("node-b");
        assertNotEquals("node-b", ring.getNode(keyToMove)); // Confirm that the same key is now assigned elsewhere. 
    }

    @Test
    void emptyRingThrowsWhenLookingUpKey() {
        // An empty ring cannot route requests, so lookup should fail clearly.
        ConsistentHashing ring = new ConsistentHashing(64);

        assertThrows(IllegalStateException.class, () -> ring.getNode("user:123"));
    }
}
