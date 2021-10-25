import java.nio.charset.StandardCharsets;

public class Server {
    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        var logger = new Logger(System.out);
        var receivedContent = new StringBuilder();

        try (var receiver = new ReliableReceiver(port, logger)) {
            logger.log("Receiver listening on port: %d", port);
            
            receiver.startListening(data -> {
                if (data.length == 0) {
                    logger.log("Total received data %s", receivedContent);
                } else {
                    var receivedString = new String(data, StandardCharsets.UTF_8);
                    receivedContent.append(receivedString);
                    
                    logger.log("Received data %s", receivedString);
                }
            });
        } catch (Exception e) {
            logger.log("An error occurred: %s", e);
        }
    }
}