import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;

/**
 * The UDPClient class represents a simple UDP client that can send and receive datagrams
 * to and from a server using a reliable communication protocol based on selective repeat.
 */
public class UDPClient {
    private static SocketAddress routerAddress;
    private static InetSocketAddress serverAddress;
    private static DatagramChannel channel;
    private static final long sequenceNumber = 2324234;
    private static long sendSeqNum;
    private static long receiveSeqNum;
    private static int serverPort;
    private static boolean verbose;

    /**
     * Constructs a new UDPClient object.
     *
     * @param routerPort     The port of the router to which the client will send datagrams.
     * @param setServerPort  The port of the server to which the client will connect.
     * @param setVerbose     A boolean flag indicating whether verbose logging is enabled.
     *                       If set to true, the client will print detailed logs.
     */
    UDPClient(int routerPort, int setServerPort, boolean setVerbose){
        serverPort = setServerPort;
        routerAddress = new InetSocketAddress("localhost", routerPort);
        serverAddress = new InetSocketAddress("localhost", serverPort);
        verbose = setVerbose;
        sendSeqNum = 1;
    }

    /**
     * Sends a request to the server using a reliable communication protocol.
     *
     * @param request The request to be sent to the server.
     */
    static void send(String request){
        try{
            channel = DatagramChannel.open();
        }catch (IOException exception){
            if(verbose)
                System.out.println("UDPCline send" + exception.getMessage());
        }
        handShake();
        if(verbose)
            System.out.println("\u001B[32m3 WAY HANDSHAKE COMPLETED\u001B[0m");
        selectiveRepeat(request);
    }

    /**
     * Performs a three-way handshake with the server to establish a connection.
     */
    private static void handShake(){
        if(verbose)
            System.out.println("\u001B[32mInitiating connection with server, port:"+ serverPort+"\u001B[0m");
        int step = 1;
        boolean connected = false;
        while(!connected) {
            try {
                switch (step) {
                    case 1:
                        Packet SYNPacket = new Packet.Builder()
                                .setType(1)
                                .setSequenceNumber(sendSeqNum)
                                .setPortNumber(serverPort)
                                .setPeerAddress(serverAddress.getAddress())
                                .setPayload("SYN".getBytes(StandardCharsets.UTF_8))
                                .create();
                        channel.send(SYNPacket.toBuffer(), routerAddress);
                        if (verbose) {
                            System.out.println("Sent SYN Packet to " + serverPort + SYNPacket);
                            System.out.println("Payload: "+new String(SYNPacket.getPayload(), StandardCharsets.UTF_8));
                        }
                        //receive the syn-ack packet before time out
                        channel.configureBlocking(false);
                        Selector selector = Selector.open();
                        channel.register(selector, OP_READ);
                        selector.select(2000);

                        Set<SelectionKey> keys = selector.selectedKeys();
                        if (keys.isEmpty()) {
                            if (verbose)
                                System.out.println("TimeOut");
                        }else {
                            ByteBuffer buffer = ByteBuffer.allocate(Packet.MAX_LEN);
                            channel.receive(buffer);
                            buffer.flip();

                            Packet response = Packet.fromBuffer(buffer);
                            if (verbose) {
                                System.out.println("\nReceived SYN-ACK from" + response.getPeerPort() + ":" + response);
                                System.out.println("Payload: "+new String(response.getPayload(), StandardCharsets.UTF_8));
                            }
                            //Received a response check if it is of type SYN-ACK and seqNumber
                            if (response.getType() == 2 && sendSeqNum == response.getSequenceNumber()) {
                                //update the step to 3 to send ACK
                                step = 3;
                            }

                            keys.clear();
                            selector.close();
                        }
                        break;
                    case 3:
                        //build ack packet
                        Packet PacketAck = new Packet.Builder()
                                .setType(3)
                                .setSequenceNumber(sendSeqNum)
                                .setPortNumber(serverPort)
                                .setPeerAddress(serverAddress.getAddress())
                                .setPayload("ACK".getBytes())
                                .create();
                        channel.send(PacketAck.toBuffer(), routerAddress);
                        if (verbose) {
                            System.out.println("\n");
                            System.out.println("Sent ACK to " + serverPort + ": " + PacketAck);
                            System.out.println("Payload: "+new String(PacketAck.getPayload(), StandardCharsets.UTF_8));
                        }
                        //connection established
                        connected = true;
                        break;
                }
            } catch (IOException exception) {
                System.out.println(exception.getMessage());
            }
        }
    }

    /**
     * Sends data reliably to the server using selective repeat.
     *
     * @param request The data to be sent.
     */
    public static void selectiveRepeat(String request){
        ReliableSRSender reliableSRSender = new ReliableSRSender(channel, serverAddress, serverPort,routerAddress, verbose);
        receiveSeqNum = reliableSRSender.send(request, sendSeqNum, sequenceNumber);
    }

    /**
     * Receives data reliably from the server using selective repeat.
     *
     * @return The received data as a String.
     */
    static String receive() {
        ReliableSRReceiver reliableSRReceiver = new ReliableSRReceiver(channel, serverAddress.getAddress(), serverPort, routerAddress, verbose);
        reliableSRReceiver.receive(receiveSeqNum, sequenceNumber, serverPort);
        return reliableSRReceiver.getData();
    }
}
