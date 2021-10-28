import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class Server {
    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        var logger = new Logger(System.out);
        
        try (var receivedContent = new ByteArrayOutputStream()) {
            try (var receiver = new ReliableReceiver(port, logger)) {
                logger.log("Receiver listening on port: %d", port);
                
                receiver.startListening(data -> {
                    if (data == null) {
                        logger.log("Total received data: %s", new String(receivedContent.toByteArray(), StandardCharsets.UTF_8));
                        receivedContent.reset();
                    } else {
                        receivedContent.write(data);
                        logger.log("Received byte %s", data);
                    }
                });
            } 
        }
        catch (Exception e) {
            logger.log("An error occurred: %s", e);
        }
    }
}