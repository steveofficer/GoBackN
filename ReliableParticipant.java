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
// such as checksum calculation, packet serialization and packet deserialization.
abstract class ReliableParticipant {
    protected final Logger _logger;

    // The packet header consists of:
    //  - A Sequence Number (int: 4 bytes)
    //  - A checksum (int: 4 bytes)
    // This is also the size of the smallest packet that we can send or receive
    // Packets with no data are used by the receiver to indicate an ACK, and by the sender to indicate a FIN.
    protected final int PACKET_HEADER_SIZE = 2 * Integer.BYTES;

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
        var packetSize = PACKET_HEADER_SIZE + (data == null ? 0 : 1);

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
        if (dataLength < PACKET_HEADER_SIZE) {
            
            _logger.log("The received packet has insufficient data.");
            return null;
        }
        
        try (var buffer = new ByteArrayInputStream(data, 0, dataLength)) {
            try (var stream = new DataInputStream(buffer)) {
                var sequenceNumber = stream.readInt();
                var expectedChecksum = stream.readInt();

                // Only read the data if it is present in the received data
                var payload = stream.available() == 0 ? null : stream.readByte();
                
                var actualChecksum = calculateChecksum(sequenceNumber, payload);
                
                if (actualChecksum != expectedChecksum) {
                    _logger.log("Corrupt packet detected.");
                    return null;
                }

                return new InboundPacket(sequenceNumber, actualChecksum, payload);
            }
        }
    }
}
