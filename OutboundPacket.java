import java.net.DatagramPacket;

public class OutboundPacket {
    private final short _sequenceNumber;
    private final DatagramPacket _packet;

    public OutboundPacket(short sequenceNumber, DatagramPacket packet) {
        _sequenceNumber = sequenceNumber;
        _packet = packet;
    }

    public short getSequenceNumber() {
        return _sequenceNumber;
    }

    public DatagramPacket getPacket() {
        return _packet;
    }
}
