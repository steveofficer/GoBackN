import java.net.DatagramPacket;

public class OutboundPacket {
    private final int _sequenceNumber;
    private final DatagramPacket _packet;

    public OutboundPacket(int sequenceNumber, DatagramPacket packet) {
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
