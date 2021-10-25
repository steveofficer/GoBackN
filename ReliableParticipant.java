import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

// This is a base class for the Sender and Receiver to centralise shared functionality
// such as checksum calculation and packet creation
abstract class ReliableParticipant {
    protected int calculateChecksum(int sequenceNumber, byte[] data) {
        var crc = new CRC32();
        crc.update(ByteBuffer.allocate(Integer.BYTES).putInt(sequenceNumber));
        crc.update(data);
        return (int)crc.getValue();
    }

    protected ReliableProtocolPacket makePacket(int sequenceNumber, byte[] data, InetAddress recipientAddress, int recipientPort) throws IOException {
        // The protocol uses packets with the following fields
        //   - Sequence Number (4 bytes)
        //   - Checksum (4 bytes)
        //   - Data
        try (var buffer = new ByteArrayOutputStream(2 * Integer.BYTES + data.length)) {
            try (var stream = new DataOutputStream(buffer)) {
                // Add sequence number to the packet
                stream.writeInt(sequenceNumber);
                
                // Compute the checksum and add it to the packet
                stream.writeInt(calculateChecksum(sequenceNumber, data));

                // Add the data to the packet
                stream.write(data);
            }

            // Return the created packet
            byte[] payload = buffer.toByteArray();
            var packet = new DatagramPacket(payload, payload.length, recipientAddress, recipientPort);
            return new ReliableProtocolPacket(sequenceNumber, packet);
        }
    }
}
