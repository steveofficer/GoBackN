import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;

// This is a base class for the Sender and Receiver to centralise shared functionality
// such as packet serialization and packet deserialization.
abstract class ReliableParticipant {
    protected final Logger _logger;

    // The packet header consists of:
    //  - A Sequence Number (int: 2 bytes)
    // This is also the size of the smallest packet that we can send or receive
    // Packets with no data are used by the receiver to indicate an ACK, and by the sender to indicate a FIN.
    protected final int PACKET_HEADER_SIZE = Short.BYTES;

    ReliableParticipant(Logger logger) {
        _logger = logger;
    }

    protected OutboundPacket makePacket(short sequenceNumber, Byte data, InetAddress recipientAddress, int recipientPort) throws IOException {
        var packetSize = PACKET_HEADER_SIZE + (data == null ? 0 : 1);

        try (var buffer = new ByteArrayOutputStream(packetSize)) {
            try (var stream = new DataOutputStream(buffer)) {
                // Add sequence number to the packet
                stream.writeShort(sequenceNumber);
                
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
                var sequenceNumber = stream.readShort();

                // Only read the data if it is present in the received data
                var payload = stream.available() == 0 ? null : stream.readByte();
                
                return new InboundPacket(sequenceNumber, payload);
            }
        }
    }
}
