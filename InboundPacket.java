public class InboundPacket {
    private final int _sequenceNumber;
    private final int _checkSum;
    private final Byte _data;

    public InboundPacket(int sequenceNumber, int checkSum, Byte data) {
        _sequenceNumber = sequenceNumber;
        _checkSum = checkSum;
        _data = data;
    }

    public int getSequenceNumber() {
        return _sequenceNumber;
    }

    public int getCheckSum() {
        return _checkSum;
    }

    public Byte getData() {
        return _data;
    }
}
