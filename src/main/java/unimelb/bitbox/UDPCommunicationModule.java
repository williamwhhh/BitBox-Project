package unimelb.bitbox;

import jdk.nashorn.internal.runtime.PrototypeObject;
import unimelb.bitbox.protocol.*;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.Timestamp;
import java.sql.Time;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The UDPCommunicationModule class handle the communication with other peers.
 * When an object of this class is initialized, other peer will be able to establish connection with me.
 * Other methods are available for sending messages to all connected peer.
 */
public class UDPCommunicationModule extends Thread implements CommunicationModule {

    private final int MAX_INCOMING_CONNECTIONS = Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
    private final int SYNC_INTERVAL = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
    private final int BUFFER_SIZE = 65536;
    private final long RETRY_INTERVAL = 3 * 1000;
    private final int MAX_RETRY = 4;

    private DatagramSocket serverSocket; // my listen socket
    private String hostAddress;
    private int hostport;
    private final HashMap<ConnectedPeer, Connection> connectedMap;

    private FileSystemManager fileSystemManager;


    /**
     * The constructor of UDPCommunicationModule.
     *
     * @param fileSystemManager A FileSystemManager object to manipulate file system.
     */
    public UDPCommunicationModule(FileSystemManager fileSystemManager) throws IOException {
        this.hostport = Integer.parseInt(Configuration.getConfigurationValue("udpPort"));
        this.serverSocket = new DatagramSocket(this.hostport);
//        this.hostAddress = InetAddress.getLocalHost().getHostAddress();
        this.hostAddress = Configuration.getConfigurationValue("advertisedName");
        this.fileSystemManager = fileSystemManager;
        this.connectedMap = new HashMap<>();


        System.out.println("My IP Address:- " + this.hostAddress);

        this.start();
    }

    /**
     * Start to listen incoming connections.
     */
    public void run() {
        try {
            listen();
        } catch (IOException e) {
            System.out.println("Problem in receiving messages\n");
        }
    }

    /**
     * Keep listen incoming connections and assign handshake task thread for them.
     */
    public void listen() throws IOException {

        byte[] buf = new byte[BUFFER_SIZE];
        // keep listening
        while (true) {
            DatagramPacket receivedPacket = new DatagramPacket(buf, buf.length);
            this.serverSocket.receive(receivedPacket);
            ConnectedPeer peer = new ConnectedPeer(receivedPacket.getAddress().getHostAddress(), receivedPacket.getPort());
            String receivedString = new String(receivedPacket.getData(), 0, receivedPacket.getLength(), StandardCharsets.UTF_8);
            synchronized (connectedMap){
                // if we have talked before
                if (connectedMap.containsKey(peer)) {
                    connectedMap.get(peer).addReceivedPending(receivedString);
                // if we have not talked before
                } else {
                    Connection connection = new Connection(peer, false, true);
                    connectedMap.put(peer, connection);
                    connectedMap.get(peer).addReceivedPending(receivedString);
                }
            }
            System.out.println("Receive From: " + receivedPacket.getAddress().getHostAddress() + ":" + receivedPacket.getPort() + "----" + "Content: " + receivedString + "\n");
        }
    }

    /**
     * Connection class, represents a particular connection with a peer.
     */
    private class Connection {

        private ConnectedPeer peer;
        private boolean isIncoming;
        private boolean isConnected;
        private ArrayList<Protocol> sentPendingList;
        private LinkedList<String> receivedPendingQueue;
        private RetryTask retryTask;
        private ProcessTask processTask;
        private SyncTask syncTask;
        private RequestHandler reqHandler;
        private BufferedWriter out;
        private String screteKey;

        /**
         * Constructor.
         * @param peer the connected peer
         * @param isConnected whether we have successfully handshake
         * @param isIncoming whether the connection is an incoming connection
         */
        public Connection(ConnectedPeer peer, boolean isConnected, boolean isIncoming) {
            commonCons(peer, isConnected, isIncoming);
        }

        /**
         * Constructor.
         * @param peer the connected peer
         * @param isConnected whether we have successfully handshake
         * @param isIncoming whether the connection is an incoming connection
         * @param out the write buffer of client
         */
        public Connection(ConnectedPeer peer, boolean isConnected, boolean isIncoming, BufferedWriter out, String screteKey) {
            commonCons(peer, isConnected, isIncoming);
            this.out = out;
            this.screteKey = screteKey;
        }

        /**
         * Common part of constructors.
         * @param peer the connected peer
         * @param isConnected whether we have successfully handshake
         * @param isIncoming whether the connection is an incoming connection
         */
        public void commonCons(ConnectedPeer peer, boolean isConnected, boolean isIncoming){
            this.peer = peer;
            this.isConnected = isConnected;
            this.isIncoming = isIncoming;
            this.sentPendingList = new ArrayList<>();
            this.receivedPendingQueue = new LinkedList<>();
            this.retryTask = new RetryTask(this);
            this.processTask = new ProcessTask(this);
            this.reqHandler = new RequestHandler(fileSystemManager);
        }

        /**
         * Send protocol message to peer.
         * @param protocol protocol need to be sent
         * @param resend whether this message is resent
         */
        public void send(Protocol protocol, boolean resend) {
            try {
                InetAddress inetAddress = InetAddress.getByName(peer.host);
                String protocolStr = protocol.toString();
                byte[] protocolByt = protocolStr.getBytes(StandardCharsets.UTF_8);
                DatagramPacket sendPacket = new DatagramPacket(protocolByt, protocolByt.length, inetAddress, peer.port);
                try {
                    System.out.println("Send To: " + peer.host + ":" + peer.port + "----" + "Content: " + protocolStr + " Is Resend: " + resend + "\n");
                    serverSocket.send(sendPacket);
                    // if send a request, add it to pendingMap
                    if (protocol.isRequest() && !resend) {
                        addSentPending(protocol);
                    }
                } catch (IOException e) {
                    System.out.println("Problem in sending messages\n");
                }
            } catch (UnknownHostException e) {
                System.out.println("The sending host is unknown\n");
            }
        }

        /**
         * Add non-responded requests to pending list.
         * @param protocol the non-responded request
         */
        private void addSentPending(Protocol protocol) {
            synchronized (sentPendingList){
                this.sentPendingList.add(protocol);
            }
        }

        /**
         * Remove responded requests from pending list.
         * @param response the response
         */
        private void removeSendPending(Protocol response) {
            synchronized (sentPendingList){
                Protocol succ = null;
                for (Protocol p : sentPendingList) {
                    if (p.pairTo(response)) {
                        succ = p;
                        break;
                    }
                }
                sentPendingList.remove(succ);
            }
        }

        /**
         * Add received message into pending list.
         * @param receivedStr received message
         */
        public void addReceivedPending(String receivedStr) {
            synchronized (this.receivedPendingQueue){
                this.receivedPendingQueue.add(receivedStr);
            }
        }

        /**
         * End connection with peer.
         */
        private void endConnection() {
            System.out.println("End connection with: " + peer.host + ":" + peer.port + "\n");
            this.writeToClint(new ConnectPeerResponse(peer.host, peer.port, false, "Peer can not be connected"));
            this.retryTask.isAlive = false;
            this.processTask.isAlive = false;
            if(this.syncTask != null){
                this.syncTask.isAlive = false;
            }
            synchronized (connectedMap){
                connectedMap.remove(peer);
            }
        }

        /**
         * Start connection with peer.
         */
        private void startConnection() {
            System.out.println("Start connection with: " + peer.host + ":" + peer.port + "\n");
            this.writeToClint(new ConnectPeerResponse(peer.host, peer.port));
            this.isConnected = true;
            synchronized (this.sentPendingList){
                this.sentPendingList = new ArrayList<>();
            }
            this.syncTask = new SyncTask(this);
        }

        /**
         * Add client write buffer.
         * @param out client write buffer
         */
        public void addClintOut(BufferedWriter out, String screteKey) {

            this.out = out;
            this.screteKey = screteKey;
        }

        /**
         * Send protocol to client.
         * @param protocol protocol need transformed.
         */
        public void writeToClint(Protocol protocol){
            if(this.out != null && this.screteKey != null){
                try {
                    EncryptHelper encryptHelper = new EncryptHelper();
                    String encryptString = encryptHelper.aesEncrypt(protocol.toString(), this.screteKey);
                    Document doc = new Document();
                    doc.append("payload", encryptString);
                    out.write(doc.toJson() + "\n");
                    out.flush();
                } catch (IOException e) {
                    System.out.println("Problem in sending messages to client\n");
                }
                this.out = null;
            }
        }

    }

    /**
     * Class of retry task, which is continuously scanning the non-responded requests and resend them if necessary.
     */
    private class RetryTask extends Thread {

        private boolean isAlive;
        private Connection connection;

        /**
         * Constructor.
         * @param connection connection being scanned.
         */
        public RetryTask(Connection connection) {
            System.out.println("Start retry monitoring task\n");
            this.isAlive = true;
            this.connection = connection;
            this.start();
        }

        /**
         * Scanning the pending list every certain interval.
         */
        public void run() {
            while (isAlive) {
                retryScan();
                try {
                    this.sleep(RETRY_INTERVAL);
                } catch (InterruptedException e) {
                    System.out.println("Problem in retry task sleeping\n");
                }
            }
            System.out.println("End retry monitoring task\n");
        }

        /**
         * Scanning the pending list.
         */
        private void retryScan() {
            synchronized (connection.sentPendingList){
                boolean needDisconnected = false;
                for (Protocol p : this.connection.sentPendingList) {
                    long currentTime = (new Date()).getTime();
                    if ((currentTime - p.getCreatedTime()) > RETRY_INTERVAL) {
                        // we can still try
                        if (p.getRetry() < MAX_RETRY) {
                            // update and resend
                            p.addRetry();
                            p.updateCreatedTime();
                            connection.send(p, true);
                        }
                        // no response for long time, disconnected.
                        else {
                            needDisconnected = true;
                            break;
                        }
                    }
                }
                if (needDisconnected) {
                    connection.endConnection();
                }
            }
        }
    }

    /**
     * Class process task, which continuously scanning the received pending list and process the
     * remaining requests.
     */
    private class ProcessTask extends Thread {

        private boolean isAlive;
        private Connection connection;

        /**
         * Constructor.
         * @param connection connection being scanned
         */
        public ProcessTask(Connection connection) {
            System.out.println("Start remaining protocol monitoring task\n");
            this.isAlive = true;
            this.connection = connection;
            this.start();
        }

        /**
         * Scan the process requests once the pending list is not empty.
         */
        public void run() {
            while (isAlive) {
                synchronized (connection.receivedPendingQueue){
                    if (connection.receivedPendingQueue.size() > 0) {
                        String receivedStr = connection.receivedPendingQueue.poll();
                        handleReceived(receivedStr);
                    }
                }
            }
            System.out.println("End remaining protocol monitoring task\n");
        }

        /**
         * Handle all received strings.
         * @param receivedString received string.
         */
        private void handleReceived(String receivedString) {
            // Invalid Protocol
            Protocol protocol = ProtocolHelper.convertProtocol(receivedString);
            if (protocol == null) {
                System.out.println("I received something strange: " + receivedString);
                handleInvalidProtocol();
                return;
            }

            // if we are not connected
            if (!connection.isConnected) {
                if (protocol instanceof HandshakeRequest) {
                    handleHandshakeRequest();
                } else if (protocol instanceof HandshakeResponse) {
                    handleHandshakeResponse();
                } else if (protocol instanceof ConnectionRefused) {
                    handleConnectionRefused(protocol);
                } else if (protocol instanceof InvalidProtocol) {
                    connection.endConnection();
                } else {
                    handleInvalidProtocol();
                }
            }
            // if we are connected
            else {
                if (protocol instanceof HandshakeRequest) {
                    handleHandshakeRequest();
                } else if (protocol instanceof HandshakeResponse) {
                    handleHandshakeResponse();
                } else if (protocol instanceof ConnectionRefused) {
                    handleConnectionRefused(protocol);
                } else if (protocol instanceof InvalidProtocol) {
                    handleInvalidProtocol();
                } else {
                    ArrayList<Protocol> reply = connection.reqHandler.handleRequest(protocol.toString());
                    for (Protocol r : reply) {
                        connection.send(r, false);
                    }
                    // if received a response, remove request from pending list
                    if (!protocol.isRequest()) {
                        connection.removeSendPending(protocol);
                    }
                }
            }

        }

        /**
         * Handle invalid protocols.
         */
        private void handleInvalidProtocol() {
            connection.send(new InvalidProtocol("invalid protocol format"), false);
            connection.endConnection();
        }

        /**
         * Handle handshake request.
         */
        private void handleHandshakeRequest() {

            // if we are connected, reply response
            if (connection.isConnected) {
                connection.send(new HandshakeResponse(new ConnectedPeer(hostAddress, hostport)), false);
            }
            // if we are not connected
            else {
                // if i can add u
                if (getIncomingConnectionsNum() < MAX_INCOMING_CONNECTIONS) {
                    connection.send(new HandshakeResponse(new ConnectedPeer(hostAddress, hostport)), false);
                    // update its info to connected
                    connection.startConnection();
                }
                // sorry i can't
                else {
                    connection.send(new ConnectionRefused(getConnectedPeers()), false);
                    connection.endConnection();
                }
            }
        }

        /**
         * Handle handshake response.
         */
        private void handleHandshakeResponse() {
            // if we are connected, maybe because i have resend request before, i just ignore it.
            if (connection.isConnected) {
                return;
            }
            // if we are not connected,
            else {
                connection.startConnection();
            }
        }

        /**
         * Handle connection refused.
         * @param protocol connection refused protocol
         */
        private void handleConnectionRefused(Protocol protocol) {
            // remove the peer from connectedMap
            connection.endConnection();
            // send handshake request to all non-requested peers
            ArrayList<ConnectedPeer> optpeers = ((ConnectionRefused) protocol).peers;
            for (ConnectedPeer p : optpeers) {
                initHandshake(p);
            }
        }
    }

    /**
     * Class represents synchronization task, which generate sync events every interval and send them to
     * every connected peers.
     */
    private class SyncTask extends Thread {

        private boolean isAlive;
        private Connection connection;

        /**
         * Constructor.
         * @param connection the corresponding connection.
         */
        public SyncTask(Connection connection) {
            System.out.println("Start sync monitoring task\n");
            this.isAlive = true;
            this.connection = connection;
            this.start();
        }

        /**
         * Run sync() every interval.
         */
        public void run() {
            while (isAlive) {
                sync();
                try {
                    this.sleep(1000 * SYNC_INTERVAL);
                } catch (InterruptedException e) {
                    System.out.println("Problem in sync task sleeping\n");
                }
            }
        }

        /**
         * Generate sync events every interval and send them to every connected peers.
         */
        private void sync() {
            System.out.println("Synchronization Task\n");
            ArrayList<FileSystemManager.FileSystemEvent> events = fileSystemManager.generateSyncEvents();
            for (FileSystemManager.FileSystemEvent e : events) {
                connection.send(EventHandler.handleEvent(e), false);
            }
        }
    }

    /**
     * Broadcast a particular protocol to every connected peers.
     * @param protocol protocol need to be sent
     */
    public void broadcast(Protocol protocol){
        synchronized (connectedMap){
            for(Connection con: connectedMap.values()){
                if(con.isConnected){
                    con.send(protocol, false);
                }
            }
        }
    }

    /**
     * Initial handshake process with a peer.
     * @param p the peer need to handshake with
     */
    public void initHandshake(ConnectedPeer p) {
        synchronized (connectedMap){
            if (!connectedMap.containsKey(p)) {
                Connection con = new Connection(p, false, false);
                connectedMap.put(p, con);
                con.send(new HandshakeRequest(new ConnectedPeer(hostAddress, hostport)), false);
            }
        }
    }

    /**
     * Initial handshake process with a peer on client's command.
     * @param p the peer need to handshake with
     * @param out the write buffer of client.
     */
    public void initHandshake(ConnectedPeer p, BufferedWriter out, String screteKey){
        synchronized (connectedMap){
            if (!connectedMap.containsKey(p)) {
                Connection con = new Connection(p, false, false, out, screteKey);
                connectedMap.put(p, con);
                con.send(new HandshakeRequest(new ConnectedPeer(hostAddress, hostport)), false);
            }
            else if(!connectedMap.get(p).isConnected){
                connectedMap.get(p).addClintOut(out, screteKey);
            }
            else {
                connectedMap.get(p).addClintOut(out, screteKey);
                connectedMap.get(p).writeToClint(new ConnectPeerResponse(p.host, p.port, false, "Peer already connected"));
            }
        }
    }

    /**
     * Disconnected with a peer.
     * @param p peer need to disconnect with
     * @return True for successfully disconnection. False for unsuccessfully disconnection.
     */
    public boolean disconnectPeer(ConnectedPeer p){
        synchronized (connectedMap){
            if(connectedMap.containsKey(p) && connectedMap.get(p).isConnected){
                connectedMap.get(p).endConnection();
                return true;
            }
            else {
                return false;
            }
        }
    }

    /**
     * Get the number of incoming peers.
     * @return the number of incoming peers
     */
    public int getIncomingConnectionsNum() {
        synchronized (connectedMap){
            int count = 0;
            for (Connection con : connectedMap.values()) {
                if (con.isConnected && con.isIncoming) {
                    count++;
                }
            }
            return count;
        }
    }

    /**
     * Get the list of connected peers.
     * @return the list of connected peers
     */
    public ArrayList<ConnectedPeer> getConnectedPeers() {
        synchronized (connectedMap){
            ArrayList<ConnectedPeer> peers = new ArrayList<>();
            for (ConnectedPeer peer : connectedMap.keySet()) {
                if (connectedMap.get(peer).isConnected) {
                    peers.add(peer);
                }
            }
            return peers;
        }
    }

}

























