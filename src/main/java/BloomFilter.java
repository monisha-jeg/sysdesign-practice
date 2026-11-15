import java.util.BitSet;

/**
 * A compact probabilistic membership filter used to answer "might contain this key?"
 * with a low false-positive rate and very fast lookups.
 */
public class BloomFilter {
    private final BitSet bits;
    private final int bitSize;
    private final int hashFunctions;

    public BloomFilter(int bitSize, int hashFunctions) {
        this.bitSize = bitSize;
        this.hashFunctions = hashFunctions;
        this.bits = new BitSet(bitSize);
    }

    public void add(String value) {
        // For each hash function, mark the corresponding bit so membership can be checked later.
        for (int i = 0; i < hashFunctions; i++) {
            int position = hash(value, i) % bitSize;
            bits.set(position);
        }
    }

    public boolean mightContain(String value) {
        // A value is likely present only if every hashed bit is set by previous inserts.
        for (int i = 0; i < hashFunctions; i++) {
            int position = hash(value, i) % bitSize;
            if (!bits.get(position)) {
                return false;
            }
        }
        return true;
    }

    private int hash(String value, int seed) {
        // Use a simple deterministic hash so repeated values map to the same bit positions.
        int hash = 0;
        for (int i = 0; i < value.length(); i++) {
            hash = (hash * 31 + value.charAt(i) + seed) & Integer.MAX_VALUE;
        }
        return hash;
    }
}
