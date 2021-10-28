import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.function.Consumer;

public class ReliableReceiver extends ReliableParticipant implements Closeable {
    private final DatagramSocket _socket;
    private final DatagramPacket _packet;
    private final Logger _logger;

    private volatile boolean _listening;

    private SocketAddress _currentClient;
    private int _expectedSequenceNumber;

    public ReliableReceiver(int port, Logger logger) throws SocketException {
        super(logger);

        // The largest packet we expect is Sequence Number + CheckSum + 1 byte of data
        var bufferSize = 2 * Integer.BYTES + 1;

        _socket = new DatagramSocket(port);
        _packet = new DatagramPacket(new byte[bufferSize], bufferSize);
        _logger = logger;

        _currentClient = null;
        _expectedSequenceNumber = 0;
    }
    
    public void startListening(Consumer<Byte> onDataReceived) throws IOException {
        _listening = true;

        while (_listening) {
            receive(onDataReceived);
        }
    }

    public void stopListening() {
        _listening = false;
    }

    private void receive(Consumer<Byte> onDataReceived) throws IOException {
        _socket.receive(_packet);
        
        var receivedPacket = deserializeDatagram(_packet);

        if (receivedPacket == null) {
            return;
        }

        if (!_packet.getSocketAddress().equals(_currentClient)) {
            _currentClient = _packet.getSocketAddress();
            _expectedSequenceNumber = 0;
            _logger.log("New client connection: %s. Expected sequence has been reset to 0.", _currentClient);
        }

        if (receivedPacket.getSequenceNumber() != _expectedSequenceNumber) {
            _logger.log("Unexpected sequence number: %d", receivedPacket.getSequenceNumber());
            resendLastAck();
            return;
        }
        
        _logger.log("Received packet has expected sequence %d and checksum: %d", receivedPacket.getSequenceNumber(), receivedPacket.getCheckSum());

        onDataReceived.accept(receivedPacket.getData());

        _logger.log("Sending ACK for %d", receivedPacket.getSequenceNumber());
        var packet = makePacket(receivedPacket.getSequenceNumber(), null, _packet.getAddress(), _packet.getPort());
        _socket.send(packet.getPacket());
        
        _expectedSequenceNumber += 1;
    }

    private void resendLastAck() throws IOException {
        if (_expectedSequenceNumber == 0) {
            return;
        }

        var lastAckedSequenceNumber = _expectedSequenceNumber - 1;

        _logger.log("Re-sending ACK for %d.", lastAckedSequenceNumber);
        var packet = makePacket(lastAckedSequenceNumber, null, _packet.getAddress(), _packet.getPort());
        _socket.send(packet.getPacket());
    }

    @Override
    public void close() throws IOException {
        _socket.close();
    }
}
