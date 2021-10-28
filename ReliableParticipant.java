import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

// This is a base class for the Sender and Receiver to centralise shared functionality
// such as checksum calculation and packet creation
abstract class ReliableParticipant {
    protected final Logger _logger;

    ReliableParticipant(Logger logger) {
        _logger = logger;
    }

    protected int calculateChecksum(int sequenceNumber, Byte data) {
        var crc = new CRC32();
        crc.update(ByteBuffer.allocate(Integer.BYTES).putInt(sequenceNumber).array());
        
        if (data != null) {
            crc.update(data);
        }
        
        return (int)crc.getValue();
    }

    protected OutboundPacket makePacket(int sequenceNumber, Byte data, InetAddress recipientAddress, int recipientPort) throws IOException {
        // The protocol uses packets with the following fields
        //   - Sequence Number (int: 4 bytes)
        //   - Checksum (int: 4 bytes)
        //   - Data (0 or 1 byte)
        var packetSize = 2 * Integer.BYTES + (data == null ? 0 : 1);
        try (var buffer = new ByteArrayOutputStream(packetSize)) {
            try (var stream = new DataOutputStream(buffer)) {
                // Add sequence number to the packet
                stream.writeInt(sequenceNumber);
                
                // Compute the checksum and add it to the packet
                stream.writeInt(calculateChecksum(sequenceNumber, data));

                // Add the data to the packet
                if (data != null) {
                    stream.write(data);
                }
            }

            // Return the created packet
            var payload = buffer.toByteArray();
            var packet = new DatagramPacket(payload, payload.length, recipientAddress, recipientPort);
            return new OutboundPacket(sequenceNumber, packet);
        }
    }

    protected InboundPacket deserializeDatagram(DatagramPacket packet) throws IOException {
        var data = packet.getData();
        var dataLength = packet.getLength();

        _logger.log("Deserialize packet with length %d", dataLength);
        if (dataLength < 2 * Integer.BYTES) {
            // The smallest packet we can receive must have at least a sequence number and checksum
            _logger.log("The received packet has insufficient data.");
            return null;
        }
        
        try (var buffer = new ByteArrayInputStream(data, 0, dataLength)) {
            try (var stream = new DataInputStream(buffer)) {
                var sequenceNumber = stream.readInt();
                var expectedChecksum = stream.readInt();
                var payload = stream.available() == 0 ? null : stream.readByte();
                
                var actualChecksum = calculateChecksum(sequenceNumber, payload);
                
                if (actualChecksum != expectedChecksum) {
                    _logger.log("Corrupt packet detected. The calculated checksum (%d) did not match the expected value (%d).", actualChecksum, expectedChecksum);
                    return null;
                }

                return new InboundPacket(sequenceNumber, actualChecksum, payload);
            }
        }
    }
}
