import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

/**
 * Verifies the cache evicts its least recently used entry when capacity is exceeded.
 */
class LRUCacheTest {

    @Test
    void cacheEvictsLeastRecentlyUsedEntry() {
        LRUCache cache = new LRUCache(2);

        cache.put("a", "one");
        cache.put("b", "two");
        cache.get("a");
        cache.put("c", "three");

        assertEquals(2, cache.size());
        assertEquals("one", cache.get("a"));
        assertEquals("three", cache.get("c"));
        assertNull(cache.get("b"));
    }
}
