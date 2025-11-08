public class SimpleTest {
    public static void main(String[] args) {
        System.out.println("Testing Java 25 compatibility!");
        
        // Test some Java 25 features
        var message = "Hello from Java 25!";
        System.out.println(message);
        
        // Test pattern matching (if available)
        Object obj = "test";
        if (obj instanceof String s) {
            System.out.println("Pattern matching works: " + s);
        }
    }
}