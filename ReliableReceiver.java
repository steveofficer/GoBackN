import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.function.Consumer;

public class ReliableReceiver extends ReliableParticipant implements Closeable {
    private final DatagramSocket _socket;
    private final DatagramPacket _packet;
    private final Logger _logger;
    private final byte[] _emptyData;

    private volatile boolean _listening;
    private ReliableProtocolPacket _lastAckedPacket;

    private int _expectedSequenceNumber;

    public ReliableReceiver(int port, Logger logger) throws SocketException {
        _socket = new DatagramSocket(port);
        _packet = new DatagramPacket(new byte[3 * Integer.BYTES], 3 * Integer.BYTES);
        _logger = logger;
        _emptyData = new byte[0];
        _lastAckedPacket = null;

        _expectedSequenceNumber = 0;
    }
    
    public void startListening(Consumer<byte[]> onDataReceived) throws IOException {
        _listening = true;

        while (_listening) {
            receive(onDataReceived);
        }
    }

    public void stopListening() {
        _listening = false;
    }

    private void receive(Consumer<byte[]> onDataReceived) throws IOException {
        _socket.receive(_packet);
        
        var data = _packet.getData();
        var dataLength = _packet.getLength();

        _logger.log("Received a packet with length %d", dataLength);

        try (var buffer = new ByteArrayInputStream(data, 0, dataLength)) {
            try (var stream = new DataInputStream(buffer)) {
                var sequenceNumber = stream.readInt();
                _logger.log("Packet has sequence number: %d", sequenceNumber);
                
                if (sequenceNumber != _expectedSequenceNumber) {
                    _logger.log("Unexpected sequence number: %d", _expectedSequenceNumber);
                    resendLastAck();
                    return;
                }
        
                var expectedChecksum = stream.readInt();
                
                var payload = stream.readAllBytes();
                
                var actualChecksum = calculateChecksum(sequenceNumber, payload);
                
                if (actualChecksum != expectedChecksum) {
                    _logger.log("The checksum (%d) did not match the expected value (%d).", actualChecksum, expectedChecksum);
                    resendLastAck();
                    return;
                }
                
                _logger.log("Received packet has expected checksum: %d", expectedChecksum);
        
                onDataReceived.accept(payload);
    
                _logger.log("Sending ACK for %d", sequenceNumber);
                _lastAckedPacket = makePacket(sequenceNumber, _emptyData, _packet.getAddress(), _packet.getPort());
                _socket.send(_lastAckedPacket.getPacket());
            }
        }
        
        _expectedSequenceNumber += 1;
    }

    private void resendLastAck() throws IOException {
        if (_lastAckedPacket == null) {
            return;
        }

        _logger.log("Re-sending ACK for %d.", _lastAckedPacket.getSequenceNumber());
        _socket.send(_lastAckedPacket.getPacket());
    }

    @Override
    public void close() throws IOException {
        _socket.close();
    }
}
