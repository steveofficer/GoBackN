import java.net.DatagramPacket;

public class ReliableProtocolPacket {
    private final int _sequenceNumber;
    private final DatagramPacket _packet;

    public ReliableProtocolPacket(int sequenceNumber, DatagramPacket packet) {
        _sequenceNumber = sequenceNumber;
        _packet = packet;
    }

    public int getSequenceNumber() {
        return _sequenceNumber;
    }

    public DatagramPacket getPacket() {
        return _packet;
    }
}
