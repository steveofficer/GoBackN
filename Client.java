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
            int windowSize = Integer.parseInt(args[2]);
            Path filePath = Paths.get(args[3]);

            // Assume that the file already contains UTF-8 encoded text and no specific re-encoding is required
            var fileContent = Files.readAllBytes(filePath);

            logger.log("Sending data from %s to %s:%s with a window size of %d", filePath, receiverAddress, receiverPort, windowSize);

            try (var client = new ReliableSender(receiverAddress, receiverPort, windowSize, logger)) {
                //Send a single byte at a time to illustrate the STOP and WAIT nature of the protocol
                for (byte b: fileContent) {
                    client.send(b);
                }

                // Send an empty packet to symbolise the end of the data
                client.send(null);

                client.waitUntilEmpty();
            }
        }
        catch (Exception e) {
            logger.log("An error occurred %s", e);
        }
    }
}