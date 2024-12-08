import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;

/**
 * The UDPServer class represents a simple UDP server that can receive datagrams
 * on a specified port and handle incoming data.
 */
public class UDPServer {
    private static long sequenceNumber;
    private static DatagramChannel channel;
    private static int serverPort;
    private static int clientPort;
    private static SocketAddress router;
    private static SocketAddress routerAddress;
    private static InetAddress clientAddress;
    private static long initialSeqNum;
    private static long sendSeqNum;
    private static boolean verbose;

    /**
     * Constructs a new UDPServer object.
     *
     * @param setServerPort The port on which the server will listen for incoming datagrams.
     * @param setVerbose    A boolean flag indicating whether verbose logging is enabled.
     *                      If set to true, the server will print detailed logs.
     */
    UDPServer(int setServerPort, boolean setVerbose) {
        sequenceNumber = 4294967295L;
        serverPort = setServerPort;
        routerAddress = new InetSocketAddress("localhost", 3000);
        verbose = setVerbose;

        try {
            channel = DatagramChannel.open();
            channel.bind(new InetSocketAddress(serverPort));
        } catch (IOException exception) {
            System.out.println("UDP sever Datagram channel exception" + exception.getMessage());
        }
    }

    /**
     * Accepts a connection request and initializes the connection.
     *
     * @return 1 if the connection is accepted successfully, 0 otherwise.
     */
    static int acceptConnectionRequest() {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(Packet.MAX_LEN).order(ByteOrder.BIG_ENDIAN);
                buffer.clear();
                SocketAddress router = channel.receive(buffer);
                if(router==null)
                    return 0;
                //accept the connection request
                buffer.flip();
                Packet packet = Packet.fromBuffer(buffer);
                buffer.flip();

                //if type = 1, new connection request
                if (packet.getType()==1) {
                    if (verbose)
                        System.out.println("\u001B[32mPort"+ serverPort + " received connection request from : \u001B[0m" + packet.getPeerPort());

                    initialSeqNum = packet.getSequenceNumber();
                    //Build SYN-ACK packet
                    Packet PacketSYN_ACK = packet.toBuilder()
                            .setType(2)
                            .setPayload("SYN-ACK".getBytes())
                            .create();
                    channel.send(PacketSYN_ACK.toBuffer(), router);
                    if (verbose) {
                        System.out.println("\nSent SYN-ACK to client "+packet.getPeerPort()+": " + PacketSYN_ACK);
                        System.out.println("Payload: " + new String(PacketSYN_ACK.getPayload(), StandardCharsets.UTF_8));
                    }
                    //retrieve the client details from the packet
                    clientAddress = packet.getPeerAddress();
                    clientPort = packet.getPeerPort();
                    UDPServer.router = router;
                    return 1;
                }

        } catch (IOException exception) {
            System.out.println("Exception at connection initialization : " + exception.getMessage());
        }
        return 0;
    }

    /**
     * Receives data reliably from the client.
     * @return The received data as a String.
     */
    static String receive() {
        ReliableSRReceiver reliableSRReceiver = new ReliableSRReceiver(channel, clientAddress, clientPort, router,verbose);
        sendSeqNum = reliableSRReceiver.receive(initialSeqNum, sequenceNumber, serverPort);
        return reliableSRReceiver.getData();
    }

    /**
     * Sends data reliably to the client.
     * @param data The data to be sent.
     */
    static void send(String data) {
        InetSocketAddress receiverAddress = new InetSocketAddress("localhost", clientPort);
        ReliableSRSender reliableSRSender = new ReliableSRSender(channel, receiverAddress, clientPort, routerAddress, verbose);
        reliableSRSender.send(data, sendSeqNum, sequenceNumber);
    }
}