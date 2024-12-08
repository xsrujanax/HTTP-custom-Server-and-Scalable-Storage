import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.charset.StandardCharsets.UTF_8;

class ReliableSRReceiver {
    private DatagramChannel channel;
    private boolean dataAvailable;
    private InetAddress clientAddress;
    private int clientPort;
    private SocketAddress routerAddress;
    private StringBuilder data;
    private boolean verbose;

    ReliableSRReceiver(DatagramChannel channel, InetAddress clientAddress, int clientPort, SocketAddress routerAddress, boolean setVerbose) {
        this.channel = channel;
        this.clientAddress = clientAddress;
        this.clientPort = clientPort;
        this.routerAddress = routerAddress;

        dataAvailable = false;
        verbose = setVerbose;
    }

    long receive(long windowBeginSeqNum, long totalSequenceNumber, int serverPort) {
        long windowSize = 4;
        data = new StringBuilder();

        HashMap<Long, Packet> currentWindowPackets = new HashMap<>();

        ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN).order(ByteOrder.BIG_ENDIAN);
        try {
            channel.configureBlocking(false);
            while (true){
                Selector selector = Selector.open();
                channel.register(selector, OP_READ);
                selector.select(1000);
                Set<SelectionKey> keys = selector.selectedKeys();

                if (keys.isEmpty()) {
                    handleTimeout(windowBeginSeqNum, serverPort);
                } else {
                    dataAvailable = true;
                    buf.clear();
                    channel.receive(buf);
                    buf.flip();
                    Packet packet = Packet.fromBuffer(buf);
                    buf.flip();
                    long seqNum = packet.getSequenceNumber();
                    if (4 == packet.getType()) {
                        Packet resp = packet.toBuilder()
                                .setType(5)
                                .setSequenceNumber(windowBeginSeqNum)
                                .setPayload("FIN_ACK".getBytes())
                                .create();
                        channel.send(resp.toBuffer(), routerAddress);
                        if (verbose) System.out.println("    " + serverPort + " sent    : " + resp);
                        return ++windowBeginSeqNum;
                    }
                    if (0 != packet.getType()) continue;
                    if (verbose) System.out.print(" Received from port: "+serverPort +" Packet: "+ packet);
                    boolean outOfOrderButWithinRange = false;
                    if (windowBeginSeqNum == seqNum) {
                        windowBeginSeqNum = handleInOrderPackets(windowBeginSeqNum, totalSequenceNumber, seqNum, packet, windowSize, currentWindowPackets);
                    } else if (windowBeginSeqNum + windowSize <= totalSequenceNumber) {
                        if (windowBeginSeqNum < seqNum && seqNum < windowBeginSeqNum + windowSize) {
                            if (verbose)
                                System.out.print(", Packet is out of order but within the range.");
                            outOfOrderButWithinRange = true;
                        } else {
                            if (verbose)
                                System.out.println(", Packet is out of order and range, discarding it.");
                        }
                    } else {
                        // out of order
                        if (verbose) System.out.print(", Packet is out of order but within the range.");
                        if (windowBeginSeqNum < seqNum && seqNum < totalSequenceNumber ||
                                0 <= seqNum && seqNum < (windowSize - (totalSequenceNumber - windowBeginSeqNum))) {
                            // within window range
                            outOfOrderButWithinRange = true;
                        } else {
                            if (verbose) System.out.println(", Packet is out of order and range, discarding it.");
                        }
                    }

                    if (outOfOrderButWithinRange) {
                        handleOutOfOrderPackets(windowBeginSeqNum, serverPort, currentWindowPackets, seqNum, packet);
                    }
                }

                keys.clear();
                selector.close();
            }
        } catch (IOException exception) {
            System.out.println(exception.getMessage());
            return -1;
        }
    }

    private long handleInOrderPackets(long windowBeginSeqNum, long totalSequenceNumber, long seqNum, Packet packet, long windowSize, HashMap<Long, Packet> currentWindowPackets) {
        // in order
        if (verbose) System.out.print("Packet is in order, deliver#" +seqNum);
        data.append(new String(packet.getPayload(), UTF_8));
        windowBeginSeqNum = (windowBeginSeqNum + 1) % totalSequenceNumber;
        // check buffer
        for (long i = 0; i < windowSize -1; ++i) {
            long bufferSeqNum = windowBeginSeqNum;
            if (currentWindowPackets.containsKey(bufferSeqNum)) {
                if (verbose) System.out.print(", #" + bufferSeqNum);
                data.append(new String(currentWindowPackets.get(bufferSeqNum).getPayload(), UTF_8));
                windowBeginSeqNum = (windowBeginSeqNum + 1) % totalSequenceNumber;
                currentWindowPackets.remove(bufferSeqNum);
            } else {
                break;
            }
        }
        if (verbose) System.out.println();
        return windowBeginSeqNum;
    }

    private void handleOutOfOrderPackets(long windowBeginSeqNum, int serverPort, HashMap<Long, Packet> currentWindowPackets, long seqNum, Packet packet) throws IOException {
        if (currentWindowPackets.containsKey(seqNum)) {
            if (verbose)
                System.out.println(", duplicate packet, discarding it");
            return;
        }
        if (verbose)
            System.out.println(", Adding to the buffer");
        // buffer it
        currentWindowPackets.put(seqNum, packet);
        // send ACK
        Packet resp = packet.toBuilder()
                .setType(3)
                .setSequenceNumber(windowBeginSeqNum)
                .setPayload("ACK".getBytes())
                .create();
        channel.send(resp.toBuffer(), routerAddress);

        if (verbose)
            System.out.println("    sent to: "+ serverPort + " " + resp);
    }

    private void handleTimeout(long windowBeginSeqNum, int serverPort) throws IOException {
        if (dataAvailable) {
            if (verbose) System.out.println("Time out");
            Packet resp = new Packet.Builder()
                    .setType(3)
                    .setSequenceNumber(windowBeginSeqNum)
                    .setPeerAddress(clientAddress)
                    .setPortNumber(clientPort)
                    .setPayload("ACK".getBytes())
                    .create();
            channel.send(resp.toBuffer(), routerAddress);
            if (verbose)
                System.out.println("    sent to: "+ serverPort + " " + resp);

        }
    }

    String getData() { return data.toString(); }
}
