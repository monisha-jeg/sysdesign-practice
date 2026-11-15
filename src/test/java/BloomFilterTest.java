import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Bloom filter behaves like a compact probabilistic set.
 */
class BloomFilterTest {

    @Test
    void bloomFilterReportsLikelyPresence() {
        BloomFilter bloom = new BloomFilter(128, 3);

        bloom.add("alpha");

        assertTrue(bloom.mightContain("alpha"));
        assertFalse(bloom.mightContain("beta"));
    }
}
