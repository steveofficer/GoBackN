public class InboundPacket {
    private final short _sequenceNumber;
    private final Byte _data;

    public InboundPacket(short sequenceNumber, Byte data) {
        _sequenceNumber = sequenceNumber;
        _data = data;
    }

    public short getSequenceNumber() {
        return _sequenceNumber;
    }

    public Byte getData() {
        return _data;
    }
}
