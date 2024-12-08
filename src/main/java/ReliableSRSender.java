import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;

/**
 * Selective Repeat Sender implementation for reliable data transfer over UDP.
 */
class ReliableSRSender {
    private final int maxPacketDataLength = 1013;
    private int currentPacketIndex;
    private int timeoutCounter;
    private static HashMap<Long, Packet> currentWindowPackets;
    private boolean requestSent;

    private DatagramChannel channel;
    private InetSocketAddress receiverAddress;
    private SocketAddress routerAddress;
    private int serverPort;

    private boolean verbose;

    /**
     * Constructs a SelectiveRepeatSender object.
     *
     * @param channel         DatagramChannel for communication.
     * @param receiverAddress InetSocketAddress of the receiver.
     * @param receiverPort    Port number of the receiver.
     * @param routerAddress   SocketAddress of the router.
     * @param setVerbose      Sets the verbose flag
     */
    ReliableSRSender(DatagramChannel channel, InetSocketAddress receiverAddress, int receiverPort, SocketAddress routerAddress, boolean setVerbose) {
        this.channel = channel;
        this.receiverAddress = receiverAddress;
        this.routerAddress = routerAddress;
        serverPort = receiverPort;

        timeoutCounter = 3;
        currentWindowPackets = new HashMap<>();
        currentPacketIndex = 0;
        requestSent = false;

        verbose = setVerbose;
    }

    /**
     * Sends data using selective repeat protocol.
     *
     * @param data                 The data to be sent.
     * @param windowSeqNum         The current sequence number for the window.
     * @param totalSequenceNumber  The total number of sequence numbers available.
     * @return The next window sequence number to be used.
     */
    long send(String data, long windowSeqNum, long totalSequenceNumber) {
        byte[] byteData = data.getBytes();
        //long windowSize = totalSequenceNumber / 2;
        long windowSize = 4;
        //calculate number of packets needed to send the data
        long packetsCount = byteData.length / maxPacketDataLength;
        if (0 != byteData.length % maxPacketDataLength)
            ++packetsCount;

        while(true) {
            try {
                // fill up / create window size Packets
                generatePackets(windowSeqNum, totalSequenceNumber, windowSize, packetsCount, byteData);

                //Request/Response data sent, receive a response before timeout
                channel.configureBlocking(false);
                Selector selector = Selector.open();
                channel.register(selector, OP_READ);
                selector.select(2000);

                Set<SelectionKey> keys = selector.selectedKeys();
                if (keys.isEmpty()) {
                    if (verbose)
                        System.out.println("Time out occurred");
                    windowSeqNum = handleTimeOut(windowSeqNum, windowSize);
                }
                else {
                    ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                    channel.receive(buf);
                    buf.flip();
                    Packet resp = Packet.fromBuffer(buf);
                    if (requestSent) {
                        if (resp.getType() ==3) {
                            channel.send(currentWindowPackets.get(windowSeqNum).toBuffer(), routerAddress);
                            if (verbose)
                                System.out.println("Sent to " + serverPort + ": " + currentWindowPackets.get(windowSeqNum));
                        } else if (5 == resp.getType()) {
                            if (verbose)
                                System.out.println("Finish sending request");
                            return ++windowSeqNum;
                        }
                    }
                    else if (resp.getType() ==3) {
                        if (verbose)
                            System.out.println("Received ACK " + resp);
                        long missedSeqNum = resp.getSequenceNumber();
                        if (currentWindowPackets.containsKey(missedSeqNum)) {
                            windowSeqNum = sendMissedPackets(windowSeqNum, totalSequenceNumber, missedSeqNum);
                        } else if (missedSeqNum == (windowSeqNum + currentWindowPackets.size()) % totalSequenceNumber) {
                            currentWindowPackets.clear();
                            windowSeqNum = missedSeqNum;
                            if (currentPacketIndex == packetsCount) {
                                sendAllPackets(windowSeqNum);
                            }
                        }
                    }
                }
                keys.clear();
                selector.close();
            } catch (IOException exception) {
                System.out.println(exception.getMessage());
            }
        }
    }

    /**
     * Resends the missed packets and adjusts the window sequence number.
     *
     * @param windowSeqNum         The current sequence number for the window.
     * @param totalSequenceNumber  The total number of sequence numbers available.
     * @param missedSeqNum         The sequence number of the missed packet.
     * @return The adjusted window sequence number.
     * @throws IOException If an I/O error occurs.
     */
    private long sendMissedPackets(long windowSeqNum, long totalSequenceNumber, long missedSeqNum) throws IOException {
        channel.send(currentWindowPackets.get(missedSeqNum).toBuffer(), routerAddress);
        if (verbose) System.out.println("Resending to " + serverPort + ": " + currentWindowPackets.get(missedSeqNum));

        long numACKed = missedSeqNum - windowSeqNum;
        for (int i = 0; i < numACKed; ++i) {
            // remove ACK'd Packets
            currentWindowPackets.remove(windowSeqNum);
            // shift the window
            windowSeqNum = (windowSeqNum + 1) % totalSequenceNumber;
        }
        return windowSeqNum;
    }

    /**
     * Handles the timeout event, either by resending packets or finishing the data transfer.
     *
     * @param windowSeqNum        The current sequence number for the window.
     * @param windowSize          The size of the sliding window.
     * @return The adjusted window sequence number.
     * @throws IOException If an I/O error occurs.
     */
    private long handleTimeOut(long windowSeqNum, long windowSize) throws IOException {
        if (requestSent) {
            windowSeqNum =handleTimeoutForRequest(windowSeqNum, currentWindowPackets);
        } else {
           sendWindowPackets(windowSeqNum, windowSize);
        }
        return windowSeqNum;
    }

    /**
     * Sends packets in the current window to the receiver.
     *
     * @param windowSeqNum The current sequence number for the window.
     * @param windowSize   The size of the sliding window.
     * @throws IOException If an I/O error occurs.
     */
    private void sendWindowPackets(long windowSeqNum, long windowSize) throws IOException {
        for (long i = 0; i < windowSize; ++i) {
            long seqNum = windowSeqNum + i;
            if (currentWindowPackets.containsKey(seqNum)) {
                channel.send(currentWindowPackets.get(seqNum).toBuffer(), routerAddress);
                if (verbose)
                    System.out.println("resending to " + serverPort + ": " + currentWindowPackets.get(seqNum));
            }
        }
    }

    /**
     * Sends the remaining packets in the window and signals the end of data transfer.
     *
     * @param windowSeqNum The current sequence number for the window.
     * @throws IOException If an I/O error occurs.
     */
    private void sendAllPackets(long windowSeqNum) throws IOException {
        Packet p = new Packet.Builder()
                .setType(4)
                .setSequenceNumber(windowSeqNum)
                .setPortNumber(receiverAddress.getPort())
                .setPeerAddress(receiverAddress.getAddress())
                .setPayload("FIN".getBytes())
                .create();
        currentWindowPackets.put(windowSeqNum, p);
        channel.send(currentWindowPackets.get(windowSeqNum).toBuffer(), routerAddress);
        if (verbose) System.out.println("Sent FIN to " + serverPort + ": " + currentWindowPackets.get(windowSeqNum));
        requestSent = true;
    }

    /**
     * Resends the packets in the window and adjusts the window sequence number.
     *
     * @param windowSeqNum        The current sequence number for the window.
     * @param currentWindowPackets The packets currently in the window.
     * @return The adjusted window sequence number.
     * @throws IOException If an I/O error occurs.
     */
    private long handleTimeoutForRequest(long windowSeqNum, HashMap<Long, Packet>currentWindowPackets) throws IOException {
        if (--timeoutCounter < 0) {
            if (verbose)
                System.out.println("Finish sending data");
            return ++windowSeqNum;
        }
        channel.send(currentWindowPackets.get(windowSeqNum).toBuffer(), routerAddress);
        if (verbose)
            System.out.println("resending to " + serverPort + ": " + currentWindowPackets.get(windowSeqNum));
        return windowSeqNum;
    }


    /**
     * Generates and sends packets for the current window of a selective repeat protocol.
     * Each packet corresponds to a segment of the total data to be transmitted.
     *
     * @param windowSeqNum         The sequence number of the first packet in the current window.
     * @param totalSequenceNumber  The total number of sequence numbers in the protocol.
     * @param windowSize           The size of the current sliding window.
     * @param packetsCount         The total number of packets needed to transmit the data.
     * @param byteData             The byte array representing the data to be transmitted.
     * @throws IOException         If an I/O error occurs while sending the packets.
     */
    private void generatePackets(long windowSeqNum, long totalSequenceNumber, long windowSize, long packetsCount, byte[] byteData) throws IOException {
        if (!requestSent) {
            for (int i = 0; i < windowSize; ++i) {
                long currentSeqNum = (windowSeqNum + i) % totalSequenceNumber;
                if (!currentWindowPackets.containsKey(currentSeqNum)) {
                    byte[] packetData;

                    //break down the data into multiple packets if the exceeds max size
                    if (currentPacketIndex < packetsCount - 1) {
                        packetData = Arrays.copyOfRange(byteData,
                                (currentPacketIndex) * maxPacketDataLength,
                                (currentPacketIndex + 1) * maxPacketDataLength);

                    } else if (currentPacketIndex == packetsCount - 1){
                        packetData = Arrays.copyOfRange(byteData,
                                (currentPacketIndex) * maxPacketDataLength,
                                byteData.length);
                    } else {
                        break;
                    }
                    ++currentPacketIndex;
                    //create a packet and send it
                    Packet packet = new Packet.Builder()
                            .setType(0)
                            .setSequenceNumber(currentSeqNum)
                            .setPortNumber(receiverAddress.getPort())
                            .setPeerAddress(receiverAddress.getAddress())
                            .setPayload(packetData)
                            .create();
                    currentWindowPackets.put(currentSeqNum, packet);
                    channel.send(currentWindowPackets.get(currentSeqNum).toBuffer(), routerAddress);
                    if (verbose) System.out.println("Sent to " + serverPort + ": " + currentWindowPackets.get(currentSeqNum));
                }
            }
        }
    }
}
