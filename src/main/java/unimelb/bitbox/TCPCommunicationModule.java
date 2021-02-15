package unimelb.bitbox;

import unimelb.bitbox.protocol.*;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The TCPCommunicationModule class handle the communication with other peers.
 * When an object of this class is initialized, other peer will be able to establish connection with me.
 * Other methods are available for sending messages to all connected peer.
 */
public class TCPCommunicationModule extends Thread implements CommunicationModule{

    private final int MAX_INCOMING_CONNECTIONS = Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
    private final int SYNC_INTERVAL = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
    // set the max pool size to be 10 times greater than max incoming connection number
    private final int MAX_POOL_SIZE = MAX_INCOMING_CONNECTIONS * 10;

    private ServerSocket listenSocket; // my listen socket
    private String hostAddress;
    private int port;

    private FileSystemManager fileSystemManager;
    private final ArrayList<Connection> connections; // storing all connected Connection Objects

    private ExecutorService handshakeExecutor; // thread pool for handshake tasks
    private String secretKey;
    EncryptHelper encryptHelper= new EncryptHelper();

    /**
     * The constructor of CommunicationModule.
     * @param fileSystemManager A FileSystemManager object to manipulate file system.
     */
    public TCPCommunicationModule(FileSystemManager fileSystemManager) throws IOException {
        this.port = Integer.parseInt(Configuration.getConfigurationValue("port"));
        this.listenSocket = new ServerSocket(this.port);
//        this.hostAddress = InetAddress.getLocalHost().getHostAddress();
        this.hostAddress = Configuration.getConfigurationValue("advertisedName");
        this.fileSystemManager = fileSystemManager;
        this.connections = new ArrayList<>();
        // using thread pool to handle handshake task
        this.handshakeExecutor = Executors.newFixedThreadPool(MAX_POOL_SIZE);

        // send sync events to all peers
        new SendSyncAllTask();

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
            System.out.println("Problem in listening connection");
        }
    }

    /**
     * Keep listen incoming connections and assign handshake task thread for them.
     * @throws IOException
     */
    public void listen() throws IOException {

        while(true) {
            Socket clientSocket = listenSocket.accept();
            System.out.println("Server receive new connection with: " + clientSocket.getInetAddress().toString() + " and " + clientSocket.getPort());
            handshakeExecutor.execute(new HandshakeTask(clientSocket, true));
        }
    }

    /**
     * Initial handshake task with a given peer.
     * @param peer The peer I need to handshake with.
     */
    public void initHandshake(ConnectedPeer peer) {
        if(peer != null){
            new InitHandShakeTask(peer);
        }
        else{
            return;
        }
    }

    public void initHandshake(ConnectedPeer peer, BufferedWriter out, String secretKey){
        this.secretKey = secretKey;
        new InitHandShakeTask(peer, out);
    }

    /**
     * Send a string to all peers connected.
     * @param protocol The protocol need to be sent.
     */
    public void broadcast(Protocol protocol) {
        synchronized (connections) {
            for(Connection c: this.connections) {
                c.send(protocol.toString());
            }
        }
    }

    /**
     * Get all connected peers.
     * @return ArrayList of connected peers
     */
    public ArrayList<ConnectedPeer> getConnectedPeers(){
        synchronized (connections){
            ArrayList<ConnectedPeer> peers = new ArrayList<>();
            for(Connection c: connections){
                peers.add(c.connectedPeer);
            }
            return peers;
        }
    }

    /**
     * Disconnect with a peer.
     * @param peer peer given
     * @return true for successfully disconnect, false for not.
     */
    public boolean disconnectPeer(ConnectedPeer peer){
        synchronized (connections){
            boolean success = false;
            for (Connection c:connections){
                if (c.connectedPeer.equals(peer)){
                    c.close();
                    success = true;
                    break;
                }
            }
            return success;
        }
    }

    /**
     * Send synchronized events to all peers connected.
     */
    public void sendSyncAll(){
        synchronized (connections) {
            for(Connection c: this.connections) {
                c.sendSync();
            }
        }
    }

    /**
     * SendSyncAllTask class represents the task of sending synchronized events to all connected peers every interval.
     */
    private class SendSyncAllTask extends Thread{
        public SendSyncAllTask(){
            this.start();
        }
        public void run(){
            while(true){
                sendSyncAll();
                try {
                    this.sleep(1000 * SYNC_INTERVAL);
                } catch (InterruptedException e) {
                    System.out.println("Problem in sleeping sync task");
                }
            }
        }
    }

    /**
     * Get the number of incoming connections.
     * @return The number of incoming connections.
     */
    private int getIncomingNum(){
        synchronized (connections){
            int r = 0;
            for(Connection c: connections){
                if(c.isIncoming){
                    r++;
                }
            }
            return r;
        }
    }

    /**
     * The class represents the task of initialing handshake with a peer. The handshake will try 5 times and 10s time
     * interval between each try.
     */
    private class InitHandShakeTask extends Thread{

        private ConnectedPeer peer;
        private int trycount;
        private BufferedWriter clientOut;

        /**
         * Constructor
         * @param peer The peer need to handshake with.
         */
        public InitHandShakeTask(ConnectedPeer peer){
            System.out.println("initHandshake Task!!");
            this.peer = peer;
            this.trycount = 5;
            this.start();
        }
        public InitHandShakeTask(ConnectedPeer peer, BufferedWriter out){
            System.out.println("initial client handshake!");
            this.peer = peer;
            this.trycount = 5;
            this.clientOut = out;
            this.start();
        }

        /**
         * Try to handshake with.
         */
        public void run() {
            while(trycount > 0){
                try{
                    synchronized (connections) {
                        // if already connected, don't initHandshake
                        for(Connection p: connections){
                            if(p.connectedPeer.equals(this.peer)){
                                    // client requested connection!!
                                sendClient("peer alreadly connected");
                                return;
                            }
                        }
                    }
                    // initialize socket for TCP connection.
                    Socket s = new Socket(peer.host, peer.port);
                    s.setSoTimeout(5 * 60 * 1000);
                    HandshakeTask h;
                    if (clientOut!=null){
                        h = new HandshakeTask(s, false, clientOut, peer);
                    }
                    else{
                        h = new HandshakeTask(s, false);
                    }
                    handshakeExecutor.execute(h);
                    h.send((new HandshakeRequest(new ConnectedPeer(hostAddress, port))).toString());
                    return;
                    // if anything bad happen, wait for 10s and try again.
                } catch (Exception e){
                    System.out.println("init handshake failed: " + e.toString());
                    trycount -= 1;
                    try {
                        this.sleep(3 * 1000);
                    } catch (InterruptedException e1) {
                        System.out.println("Problem in sleeping handshake task");
                    }
                }
            }
            if (clientOut != null){
                sendClient("Initial Tcp connection failed");
            }
        }

        /**
         * Client send message to peer.
         * @param message message given
         */
        public void sendClient(String message){
            try {
                String data = (new ConnectPeerResponse(peer.host, peer.port, false, message)).toString();
                String encrypted = encryptHelper.aesEncrypt(data, secretKey);
                Document doc = new Document();
                doc.append("payload", encrypted);
                clientOut.write(doc.toJson() + "\n");
                clientOut.flush();
            } catch (Exception e){
                System.out.println("Client connection lost");
            }
        }
    }

    /**
     * The class represent a connection with peer and handle all communication issues.
     */
    private class Connection extends Thread {

        private BufferedReader in;
        private BufferedWriter out;
        private Socket clientSocket;
        private RequestHandler requestHandler;
        private ConnectedPeer connectedPeer;
        private boolean isIncoming; // indicate whether the connection is an incoming connection

        /**
         * Constructor
         * @param aClientSocket The socket of the connected peer.
         * @param in The income BufferedReader.
         * @param out The outcome BufferedWriter.
         * @param connectedPeer The connected peer, including host address and port number.
         * @param isIncoming Indicate whether the connection is incoming connection.
         * @throws IOException
         */
        public Connection(Socket aClientSocket, BufferedReader in, BufferedWriter out, ConnectedPeer connectedPeer, boolean isIncoming) throws IOException {

            System.out.println("new connection thread created");

            this.clientSocket = aClientSocket;
            // remove timeout for socket
            this.clientSocket.setSoTimeout(0);
            this.requestHandler = new RequestHandler(fileSystemManager);
            this.in = in;
            this.out = out;
            this.connectedPeer = connectedPeer;
            this.isIncoming = isIncoming;
            // add itself into connections
            synchronized (connections){
                connections.add(this);
            }
            // start itself
            this.start();
            // send sync events
            sendSync();
        }

        /**
         * Send string to peer.
         * @param data The string need to be sent.
         */
        public void send(String data){
            try {
                out.write(data + "\n");
                out.flush();
                System.out.println("I send: " + data);
                // something bad happened, close the connection.
            } catch (IOException e) {
                System.out.println("Problem in sending data");
                close();
            }
        }

        /**
         * Run the connection.
         */
        public void run() {
            System.out.println("Running one connection task");
            while(true) {
                try {
                    // receive
                    String data = in.readLine();
                    System.out.println("I receive data: " + data);
                    if (data == null || !checkReveive(data)){
                        System.out.println("check message error");
                        break;
                    }
                    // send
                    ArrayList<Protocol> needSendProtocol = requestHandler.handleRequest(data);
                    // send every message in order
                    if(needSendProtocol.size() != 0){
                        for(Protocol p:needSendProtocol){
                            send(p.toString());
                        }
                    }
                    // something bad happened, close it.
                } catch (IOException e) {
                    System.out.println("Connection lost by: " + e.toString());
                    close();
                    return;
                }
            }
            close();
        }

        /**
         * Check whether a received string is valid and meaningful.
         * @param data
         * @return
         */
        private boolean checkReveive(String data){
            Protocol p = ProtocolHelper.convertProtocol(data);
            // if it is invalid protocols
            if(p == null || p instanceof HandshakeRequest || p instanceof HandshakeResponse || p instanceof ConnectionRefused){
                send(new InvalidProtocol("Invalid Protocol").toString());
                return false;
            }
            // if it is InvalidProtocol
            else if(p instanceof InvalidProtocol){
                return false;
            }
            else{
                return true;
            }
        }

        /**
         * Send sync events.
         */
        public void sendSync(){
            ArrayList<FileSystemManager.FileSystemEvent> events = fileSystemManager.generateSyncEvents();
            for(FileSystemManager.FileSystemEvent e: events){
                send(EventHandler.handleEvent(e).toString());
            }
        }

        /**
         * Close the connection and remove it from connections.
         */
        private void close(){
            System.out.println("A connection close");
            try {
                this.clientSocket.close();
            } catch (IOException e) {
                System.out.println("Problem in closing socket");
            }
            // delete itself
            synchronized (connections){
                connections.remove(this);
            }
        }
    }

    /**
     * Class represents the handshake task.
     */
    private class HandshakeTask implements Runnable{

        private BufferedReader in;
        private BufferedWriter out;
        private Socket clientSocket;
        private boolean isIncoming;
        private BufferedWriter clientOut;
        private ConnectedPeer peer;

        /**
         * Constructor
         * @param aClientSocket The socket of connected peer.
         * @param isIncoming Indicate whether the handshake is incoming request.
         * @throws IOException
         */
        public HandshakeTask(Socket aClientSocket, boolean isIncoming) throws IOException {
            this.clientSocket = aClientSocket;
            this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            this.out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8));
            this.isIncoming = isIncoming;
        }

        public HandshakeTask(Socket aClientSocket, boolean isIncoming, BufferedWriter clientOut, ConnectedPeer peer) throws IOException {
            this.clientSocket = aClientSocket;
            this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            this.out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8));
            this.isIncoming = isIncoming;
            this.clientOut = clientOut;
            this.peer = peer;
        }

        public void sendClient(String message){
            try {
                String data = (new ConnectPeerResponse(peer.host, peer.port, false, message)).toString();
                String encrypted = encryptHelper.aesEncrypt(data, secretKey);
                Document doc = new Document();
                doc.append("payload", encrypted);
                clientOut.write(doc.toJson() + "\n");
                clientOut.flush();
            }catch(Exception e){
                System.out.println("Client connection lost");
            }
        }
        public void sendClient(){
            try {
                String data = (new ConnectPeerResponse(peer.host, peer.port)).toString();
                String encrypted = encryptHelper.aesEncrypt(data, secretKey);
                Document doc = new Document();
                doc.append("payload", encrypted);
                clientOut.write(doc.toJson() + "\n");
                clientOut.flush();
            } catch (Exception e){
                System.out.println(("Client connection lost"));
            }
        }

        /**
         * Send message to peer.
         * @param data The message need to be send.
         */
        public void send(String data){
            try {
                out.write(data + "\n");
                out.flush();
                System.out.println("I send: " + data);
            } catch (IOException e) {
                close();
                System.out.println("Problem in sending data");
            }
        }

        /**
         * Run the handshake procedure.
         */
        public void run(){
            System.out.println("Running one handshake task");
            try {
                String data = in.readLine();
                // socket has been closed
                if(data == null){
                    if (clientOut != null){
                        sendClient("Tcp connection lost");
                    }
                    close();
                    return;
                }
                System.out.println("Im waiting for handshake, receive data: " + data);
                handshakeHandler(data);
                System.out.println("handshake finish");
                return;
            } catch (IOException e) {
                System.out.println("Connection lost by: " + e.toString());
                if (clientOut != null){
                    sendClient("Tcp connection lost");
                }
                close();
                return;
            }
        }

        /**
         * Close the socket.
         */
        private void close(){
            try {
                this.clientSocket.close();
            } catch (IOException e) {
                System.out.println("Problem in closing socket");
            }
        }

        /**
         * Handle message received.
         * @param str The message received.
         * @throws IOException
         */
        private void handshakeHandler(String str) throws IOException {
            Protocol protocol = ProtocolHelper.convertProtocol(str);
            // receive invalid protocol
            if(protocol == null){
                handleInvalidProtocol();
            }
            // receive handshake response
            else if(protocol instanceof HandshakeResponse){
                // this is an outgoing connection
                if(!this.isIncoming){
                    handleHandshakeResponse(protocol);
                }
                else{
                    handleInvalidProtocol();
                }
            }
            // receive handshake request
            else if(protocol instanceof HandshakeRequest){
                // this is an incoming connection
                if(this.isIncoming){
                    handleHandshakeRequest(protocol);
                }
                else{
                    handleInvalidProtocol();
                }
            }
            // receive connection refused
            else if(protocol instanceof ConnectionRefused){
                // this is an outgoing connection
                if(!this.isIncoming){
                    handleConnectionRefused(protocol);
                }
                else{
                    handleInvalidProtocol();
                }
            }
            // other protocol type
            else{
                handleInvalidProtocol();
            }
        }

        /**
         * Send InvalidProtocol when receive invalid protocol.
         */
        private void handleInvalidProtocol() {
            send((new InvalidProtocol("Invalid Protocol when handshaking")).toString());
            if (clientOut != null){
                sendClient("Connection failed");
            }
            close();
        }

        /**
         * Start connection when receiving Handshake Response.
         * @param protocol The protocol received.
         * @throws IOException
         */
        private void handleHandshakeResponse(Protocol protocol) throws IOException {
            HandshakeResponse handshakeResponse = (HandshakeResponse) protocol;
            ConnectedPeer peer = handshakeResponse.peer;
            startConnection(peer);
            if (clientOut != null){
                sendClient();;
            }
        }

        /**
         * Handle Handshake Request.
         * @param protocol The protocol received.
         * @throws IOException
         */
        private void handleHandshakeRequest(Protocol protocol) throws IOException {
            synchronized (connections) {
                HandshakeRequest handshakeRequest = (HandshakeRequest) protocol;
                ConnectedPeer requestPeer = handshakeRequest.peer;
                // if i haven't connected this peer, and remain some incoming slots.
                if(!connections.contains(requestPeer) && getIncomingNum() < MAX_INCOMING_CONNECTIONS){
                    // send response and start connection
                    send((new HandshakeResponse(new ConnectedPeer(hostAddress, port))).toString());
                    startConnection(requestPeer);
                    if (clientOut != null){
                        sendClient();
                    }
                }
                // I can NOT accept this request
                else{
                    ArrayList<ConnectedPeer> peers = new ArrayList<>();
                    for(Connection c: connections){
                        peers.add(c.connectedPeer);
                    }
                    send((new ConnectionRefused(peers)).toString());
                    close();
                }
            }
        }

        /**
         * Send Handshake Request to all peers in ConnectionRefused.
         * @param protocol The protocol received.
         */
        private void handleConnectionRefused(Protocol protocol){
            ArrayList<ConnectedPeer> peers = ((ConnectionRefused) protocol).peers;
            // send handshake request to all peers
            for(ConnectedPeer p: peers){
                initHandshake(p);
            }
            if (clientOut != null){
                sendClient("Peer incoming connection full");
            }
            close();
        }

        /**
         * Start connection with a handshaked peer.
         * @param peer The peer need to connected with.
         * @throws IOException
         */
        private void startConnection(ConnectedPeer peer) throws IOException {
            new Connection(clientSocket, in, out, peer, isIncoming);
        }

    }
}
