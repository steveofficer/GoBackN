import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Client {
    public static void main(String[] args)
    {
        var logger = new Logger(System.out);
        
        try {
            InetAddress receiverAddress = InetAddress.getByName(args[0]);
            int receiverPort = Integer.parseInt(args[1]);
            Path filePath = Paths.get(args[2]);
            var fileContent = Files.readAllBytes(filePath);

            int windowSize = 5;

            logger.log("Sending %s to %s:%s with a window size %d", filePath, receiverAddress, receiverPort, windowSize);

            try (var client = new ReliableSender(receiverAddress, receiverPort, windowSize, logger)) {
                //Send a single byte at a time to illustrate the STOP and WAIT nature of the protocol
                for (byte b: fileContent) {
                    client.send(b);
                }

                // Send an empty packet to symbolise the end of the data
                client.send(null);

                logger.log("Waiting for all packets to be delivered.");
                client.waitUntilEmpty();
    
                logger.log("Done");
            }
        }
        catch (Exception e) {
            logger.log("An error occurred %s", e);
        }
    }
}