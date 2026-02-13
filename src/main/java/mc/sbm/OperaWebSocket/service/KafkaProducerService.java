package mc.sbm.OperaWebSocket.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class KafkaProducerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${oracle.hospitality.kafka.topic}")
    private String topic;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends a message to Kafka topic asynchronously
     *
     * @param key Message key (can be null for round-robin partitioning)
     * @param message Message content
     */
    public void sendMessage(String key, String message) {
        try {
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, key, message);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    logger.debug("Message sent successfully to topic '{}' - Partition: {}, Offset: {}, Key: {}",
                            topic,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            key);
                } else {
                    logger.error("Failed to send message to Kafka topic '{}' with key '{}'", topic, key, ex);
                }
            });
        } catch (Exception e) {
            logger.error("Error sending message to Kafka", e);
        }
    }

    /**
     * Sends a message to a dynamically specified topic
     *
     * @param topic Topic name
     * @param key Message key (can be null for round-robin partitioning)
     * @param message Message content
     */
    public void sendMessageToTopic(String topic, String key, String message) {
        try {
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, key, message);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    logger.debug("Message sent successfully to topic '{}' - Partition: {}, Offset: {}, Key: {}",
                            topic,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            key);
                } else {
                    logger.error("Failed to send message to Kafka topic '{}' with key '{}'", topic, key, ex);
                }
            });
        } catch (Exception e) {
            logger.error("Error sending message to Kafka topic '{}'", topic, e);
        }
    }

    /**
     * Sends a message to Kafka topic without a key
     *
     * @param message Message content
     */
    public void sendMessage(String message) {
        sendMessage(null, message);
    }

    /**
     * Sends a message synchronously (blocks until confirmation)
     * Use this for critical messages where you need delivery confirmation
     *
     * @param key Message key
     * @param message Message content
     * @return true if sent successfully, false otherwise
     */
    public boolean sendMessageSync(String key, String message) {
        try {
            SendResult<String, String> result = kafkaTemplate.send(topic, key, message).get();
            logger.info("Message sent synchronously to topic '{}' - Partition: {}, Offset: {}",
                    topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            return true;
        } catch (Exception e) {
            logger.error("Failed to send message synchronously to Kafka", e);
            return false;
        }
    }
}