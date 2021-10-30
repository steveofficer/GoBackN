import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;
import java.util.ArrayList;
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
    private final LinkedBlockingQueue<OutboundPacket> _buffer;
    
    // This is a timer that will cause unacknowledged packets to be resent
    private Timer _timer;

    // This is a counter that keeps track of the next sequence number to use
    private short _sequenceNumber;

    // This indicates to background threads that the sender is being closed and the thread should stop
    private volatile boolean _closed;

    public ReliableSender(InetAddress serverAddress, int serverPort, int windowSize, Logger logger) throws SocketException {
        super(logger);

        _logger = logger;

        _closed = false;
        
        _serverAddress = serverAddress;
        _serverPort = serverPort;
        _socket = new DatagramSocket();
        _bufferEmpty = new Object();

        _buffer = new LinkedBlockingQueue<OutboundPacket>(windowSize);
        _sequenceNumber = 0;
        
        var controlChannel = new Thread(() -> {
            // The packets we expect to receive only have a packet header.
            // Any data portion that gets received will be ignored.
            var packet = new DatagramPacket(new byte[PACKET_HEADER_SIZE], PACKET_HEADER_SIZE);

            while (!_closed) {
                try {
                    try {
                        _socket.receive(packet);
                    } catch (SocketException e) {
                        // This exception occurs when the socket closes.
                        // We can ignore it and the loop will exit.
                        continue;
                    }

                    if (_buffer.isEmpty()) {
                        // If the buffer is empty then we do not need to inspect the contents of the packets
                        _logger.log("Ignoring packet, there are no unacknowledged packets");
                    } else {
                        // We have unacknowledged packets so we need to deserialize the packet and deal with it
                        // appropriately
                        handleACKPacket(packet);
                    }
                } catch (IOException e) {
                    _logger.log("An error occurred on the control channel %s", e);
                }
            }
        });
        
        controlChannel.start();
    }
    
    public void send(Byte data) throws IOException, InterruptedException {
        var packet = makePacket(_sequenceNumber, data, _serverAddress, _serverPort);

        // Keep a copy of the packet in case in needs to be resent
        // using put blocks the thread if the buffer has reached capacity and waits for space
        _buffer.put(packet);

        // Send the packet
        _logger.log("Sending packet with sequence number %d", _sequenceNumber);
        _socket.send(packet.getPacket());

        _sequenceNumber += 1;

        startTimer();
    }

    public void waitUntilEmpty() throws InterruptedException {
        _logger.log("Waiting for all packets to be delivered.");
        synchronized(_bufferEmpty) {
            while (!_buffer.isEmpty()) {
                _bufferEmpty.wait();
            }
        }
    }

    private synchronized void startTimer() {
        if (_timer == null) {
            _timer = new Timer();
            _timer.scheduleAtFixedRate(
                new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            _logger.log("Time out. Resending unacknowledged packets.");
    
                            for (var packet: _buffer) {
                                _socket.send(packet.getPacket());
                                _logger.log("Re-sent packet %d", packet.getSequenceNumber());
                            }
                        } catch (IOException e) {
                            _logger.log("An error occurred while resending unacknowledged packets: %s", e);
                        }
                    }
                }, 
                2000, 
                2000
            );
        }
    }

    private void handleACKPacket(DatagramPacket packet) throws IOException {
        stopTimer();
        
        try {
            var receivedPacket = deserializeDatagram(packet);
            if (receivedPacket == null) {
                return;
            }

            _logger.log("Received ACK for sequence number %d", receivedPacket.getSequenceNumber());

            // Look for the packet(s) that has been ACKed.
            // By examining the oldest packet we have, we can calculate the full range of sequence numbers and where they are
            // in the queue.
            var oldestUnackedPacket = _buffer.peek();

            if (oldestUnackedPacket.getSequenceNumber() > receivedPacket.getSequenceNumber()) {
                _logger.log("The packet with sequence number %d has already been ACKED", receivedPacket.getSequenceNumber());
                return;
            }

            // Calculate how many packets we can acknowledge
            var ackedPacketCount = (receivedPacket.getSequenceNumber() - oldestUnackedPacket.getSequenceNumber()) + 1;

            if (ackedPacketCount > _buffer.size()) {
                // The sequence number is for a packet that hasn't been sent yet
                _logger.log("The packet with sequence number %d has never been sent", receivedPacket.getSequenceNumber());
                return;
            }

            if (ackedPacketCount == 1) {
                _logger.log("Packet %d has been ACKED", receivedPacket.getSequenceNumber());
            } else {
                _logger.log("%d packets between %d and %d have been ACKED", ackedPacketCount, oldestUnackedPacket.getSequenceNumber(), receivedPacket.getSequenceNumber());
            }
            
            Collection<OutboundPacket> ackedPackets = new ArrayList<OutboundPacket>(ackedPacketCount);
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

    private synchronized void stopTimer() {
        if (_timer != null) {
            _timer.cancel();
            _timer = null;
        }
    }

    @Override
    public void close() throws IOException {
        _closed = true;
        stopTimer();
        _socket.close();
        _logger.log("Sender closed.");
    }
}