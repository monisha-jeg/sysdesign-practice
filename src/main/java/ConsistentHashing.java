import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A simple consistent hashing implementation using a ring of hashed virtual nodes.
 * This approach minimizes data movement when nodes are added or removed from the cluster.
 */
public class ConsistentHashing {
    // Sorted map of hash -> node, representing the virtual-node hash ring.
    private final SortedMap<Long, String> ring = new TreeMap<>();

    // Number of virtual copies per physical node to improve distribution balance.
    private final int virtualNodes;

    public ConsistentHashing(int virtualNodes) {
        this.virtualNodes = virtualNodes;
    }

    /**
     * Adds a physical node to the ring by creating several virtual nodes.
     */
    public void addNode(String node) {
        for (int i = 0; i < virtualNodes; i++) {
            String virtualNode = node + "#" + i;
            long hash = hash(virtualNode);
            ring.put(hash, node);
        }
    }

    /**
     * Removes a physical node from the ring by deleting all of its virtual node hashes.
     */
    public void removeNode(String node) {
        for (int i = 0; i < virtualNodes; i++) {
            String virtualNode = node + "#" + i;
            long hash = hash(virtualNode);
            ring.remove(hash);
        }
    }

    /**
     * Finds the node responsible for a given key by walking the ring clockwise.
     * If the key's hash is greater than the last ring entry, wrap back to the first entry.
     */
    public String getNode(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("No nodes are present in the ring");
        }

        SortedMap<Long, String> tailMap = ring.tailMap(hash(key));
        return !tailMap.isEmpty() ? tailMap.get(tailMap.firstKey()) : ring.get(ring.firstKey());
    }

    public int getVirtualNodes() {
        return virtualNodes;
    }

    /**
     * Produces a stable hash for a string using SHA-256.
     */
    private long hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));

            long hash = 0L;
            // SHA-256 produces a 32-byte digest. We only need the first 8 bytes here to
            // create a compact numeric key for the ring. Each iteration shifts the current
            // value left by 8 bits and ORs in the next byte, effectively building a long
            // from the byte sequence: [b0, b1, b2, b3, b4, b5, b6, b7].
            // Example: hash = (hash << 8) | (byte & 0xff) keeps the full value while
            // preserving each byte's unsigned contribution.
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (hashBytes[i] & 0xffL);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static void main(String[] args) {
        // Create a ring with 4 physical nodes, each represented by 128 virtual nodes.
        // More virtual nodes gives a more even spread of keys across the cluster.
        ConsistentHashing consistentHashing = new ConsistentHashing(128);
        List<String> nodes = List.of("NodeA", "NodeB", "NodeC", "NodeD");

        // Add each physical node to the hash ring so it can receive assigned keys.
        for (String node : nodes) {
            consistentHashing.addNode(node);
        }

        // Create 1,000 sample keys to simulate real traffic.
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            keys.add("key-" + i);
        }

        // Map to test how many generated keys land on each node.
        Map<String, Integer> testKeyDistributionMap = new HashMap<>();
        for (String node : nodes) {
            testKeyDistributionMap.put(node, 0);
        }

        // For each key, find the node it maps to and increment the count for that node.
        for (String key : keys) {
            String owner = consistentHashing.getNode(key);
            testKeyDistributionMap.put(owner, testKeyDistributionMap.getOrDefault(owner, 0) + 1);
        }

        // Print the resulting distribution to show how evenly the keys are spread.
        System.out.println("Consistent hashing distribution:");
        for (String node : nodes) {
            System.out.println(node + " -> " + testKeyDistributionMap.get(node) + " keys");
        }

        // Show a specific example key and the node that owns it.
        String key = "user:42";
        System.out.println("Key '" + key + "' maps to " + consistentHashing.getNode(key));
    }
}
