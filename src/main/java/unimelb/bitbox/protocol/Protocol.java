package unimelb.bitbox.protocol;

/**
 * Interface for all Protocol classes
 */
public interface Protocol {
    /**
     * Convert a protocol to string.
     * @return The corresponding string.
     */
    String toString();

    boolean isRequest();

    long getCreatedTime();

    void updateCreatedTime();

    int getRetry();

    void addRetry();

    boolean pairTo(Protocol p);

}
