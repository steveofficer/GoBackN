import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.Collection;
import java.util.ArrayList;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class ReliableSender extends ReliableParticipant implements java.io.Closeable {
    private final InetAddress _serverAddress; 
    private final int _serverPort;
    private final DatagramSocket _socket;
    private final Logger _logger;

    // This acts as a mutual exclusion condition to allow waitUntilEmpty to detect the buffer being empty in a thread-safe manner
    // without using a busy-wait loop
    private final Object _bufferEmpty;

    // Blocking Queue has a maximum capacity that when it is reached will block the thread and wait for space.
    // This allows us to stop sending packets when the buffer is full and will continue when the buffer has space.
    // This is in line with the comment from the textbook. It could throw an exception and have a loop that repeatedly tries
    // to add, or, the chosen approach, block the thread and wait.
    private final LinkedBlockingQueue<ReliableProtocolPacket> _buffer;
    
    // This is a timer that will cause unacknowledged packets to be resent
    private final ScheduledThreadPoolExecutor _timer;

    // This is a counter that keeps track of the sequence number
    private int _sequenceNumber;

    // This indicates to background threads that the sender is being closed and the thread should stop
    private volatile boolean _closed;

    public ReliableSender(InetAddress serverAddress, int serverPort, int windowSize, Logger logger) throws SocketException {
        _logger = logger;

        _closed = false;
        
        _serverAddress = serverAddress;
        _serverPort = serverPort;
        _socket = new DatagramSocket();
        _bufferEmpty = new Object();

        _buffer = new LinkedBlockingQueue<ReliableProtocolPacket>(windowSize);
        _sequenceNumber = 0;
        
        _timer = new ScheduledThreadPoolExecutor(1);
        
        var controlChannel = new Thread(() -> {
            var packet = new DatagramPacket(new byte[150], 150);
            while (!_closed) {
                try {
                    _socket.receive(packet);
                    handleReceivedPacket(packet);
                } catch (IOException e) {
                }
            }
        });
        
        controlChannel.start();
    }
    
    public void send(byte[] data) throws IOException, InterruptedException {
        var packet = makePacket(_sequenceNumber, data, _serverAddress, _serverPort);

        // Keep a copy of the packet in case in needs to be resent
        // using put blocks the thread if the buffer has reached capacity and waits for space
        _buffer.put(packet);

        // Send the packet
        _logger.log("Sending packet with sequence number %d", _sequenceNumber);
        _socket.send(packet.getPacket());

        _sequenceNumber += 1;

        // Start the the timer if it isn't already running.
        if (_timer.getQueue().isEmpty()) {
            _logger.log("Starting timer.");
            startTimer();
        }
    }

    public void waitUntilEmpty() throws InterruptedException {
        synchronized(_bufferEmpty) {
            while (!_buffer.isEmpty()) {
                _bufferEmpty.wait();
            }
        }
    }

    private void startTimer() {
        _timer.scheduleAtFixedRate(
            () -> {
                try {
                    _logger.log("Time out. Resending unacknowledged packets.");

                    for (var packet: _buffer) {
                        _socket.send(packet.getPacket());
                        _logger.log("Re-sent packet %d", packet.getSequenceNumber());
                    }
                } catch (IOException e) {
                }
            }, 
            2, 
            2, 
            TimeUnit.SECONDS
        );
    }

    private void handleReceivedPacket(DatagramPacket packet) throws IOException {
        stopTimer();
        
        var data = packet.getData();
        var dataLength = packet.getLength();

        _logger.log("Received a packet with length %d", dataLength);

        try (var stream = new DataInputStream(new ByteArrayInputStream(data, 0, packet.getLength()))) {
            var ackedSequenceNumber = stream.readInt();
            _logger.log("Received packet has sequence number: %d", ackedSequenceNumber);
            
            var expectedChecksum = stream.readInt();
            var receivedPayload = stream.readAllBytes();
            var actualChecksum = calculateChecksum(ackedSequenceNumber, receivedPayload);
            
            if (actualChecksum != expectedChecksum) {
                _logger.log("Ignoring received packet. The checksum (%d) did not match the expected value (%d).", actualChecksum, expectedChecksum);
                return;
            }

            _logger.log("Received Packet has expected checksum: %d", expectedChecksum);

            // Look for the packet(s) that has been ACKed.
            // By examining the oldest packet we have, we can calculate the full range of sequence numbers and where they are
            // in the queue.
            var pendingPacket = _buffer.peek();

            if (pendingPacket == null || pendingPacket.getSequenceNumber() > ackedSequenceNumber) {
                // The buffer is empty or the sequence number is earlier than the oldest sequence number that is pending
                return;
            }

            // Calculate how many packets we can acknowledge
            var ackedPacketCount = (ackedSequenceNumber - pendingPacket.getSequenceNumber()) + 1;

            if (ackedPacketCount > _buffer.size()) {
                // The acked sequence number is further into the future so the packet has been sent yet
                return;
            }

            _logger.log("ACKING %d packets between %d and %d", ackedPacketCount, pendingPacket.getSequenceNumber(), ackedSequenceNumber);
            Collection<ReliableProtocolPacket> ackedPackets = new ArrayList<ReliableProtocolPacket>(ackedPacketCount);
            _buffer.drainTo(ackedPackets, ackedPacketCount);
        } finally {
            if (!_buffer.isEmpty()) {
                // If there are still items waiting to be ACKed then start the timer that will resend them
                startTimer();
            } else {
                // If the buffer is empty then notify any threads that are waiting
                synchronized(_bufferEmpty) {
                    _bufferEmpty.notifyAll();
                }
            }
        }
    }

    private void stopTimer() {
        _timer.getQueue().clear();
    }

    @Override
    public void close() throws IOException {
        _closed = true;
        _timer.shutdownNow();
        _socket.close();
    }
}