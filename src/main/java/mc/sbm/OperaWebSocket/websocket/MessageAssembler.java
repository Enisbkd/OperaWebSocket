package mc.sbm.OperaWebSocket.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles message fragmentation and reassembly for large WebSocket payloads
 */
public class MessageAssembler {

    private static final Logger logger = LoggerFactory.getLogger(MessageAssembler.class);
    private static final int MAX_MESSAGE_BUFFER_SIZE = 50 * 1024 * 1024; // 50MB limit

    private final StringBuilder messageBuffer = new StringBuilder();

    /**
     * Appends a message fragment to the buffer
     *
     * @param fragment the message fragment
     * @throws IllegalStateException if buffer size exceeds maximum
     */
    public synchronized void appendFragment(String fragment) {
        if (messageBuffer.length() + fragment.length() > MAX_MESSAGE_BUFFER_SIZE) {
            clear();
            throw new IllegalStateException("Message buffer size exceeded maximum limit");
        }
        messageBuffer.append(fragment);
        logger.debug("Appended fragment ({} bytes), total buffer size: {} bytes",
                fragment.length(), messageBuffer.length());
    }

    /**
     * Gets the complete assembled message and clears the buffer
     *
     * @return the complete message
     */
    public synchronized String getCompleteMessage() {
        String completeMessage = messageBuffer.toString();
        messageBuffer.setLength(0);
        logger.debug("Assembled complete message ({} bytes)", completeMessage.length());
        return completeMessage;
    }

    /**
     * Checks if buffer contains any fragments
     *
     * @return true if buffer has data
     */
    public synchronized boolean hasFragments() {
        return messageBuffer.length() > 0;
    }

    /**
     * Clears the message buffer
     */
    public synchronized void clear() {
        if (messageBuffer.length() > 0) {
            logger.warn("Clearing message buffer ({} bytes lost)", messageBuffer.length());
            messageBuffer.setLength(0);
        }
    }

    /**
     * Gets current buffer size
     *
     * @return buffer size in bytes
     */
    public synchronized int getBufferSize() {
        return messageBuffer.length();
    }
}