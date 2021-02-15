package unimelb.bitbox;

import unimelb.bitbox.protocol.*;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * The ClientManager class handle the communication with the clients.
 * When an object of this class is initialized, other clients will be able to establish connection with me.
 * Other methods are available for sending messages to all connected peer.
 */
public class ClientManager extends Thread{
    private CommunicationModule communicationModule;
    private ServerSocket listenSocket;
    private int clientPort;
    private HashMap<String, PublicKey> clientKeys;
    private final Object clientlock = new Object();
    private boolean clientOccurs;
    private EncryptHelper encryptHelper;
    /**
     * The constructor of Client.
     * @param communicationModule A communicaton module.
     */
    public ClientManager(CommunicationModule communicationModule) throws IOException {
        this.communicationModule = communicationModule;
        this.clientPort = Integer.parseInt(Configuration.getConfigurationValue("clientPort"));
        this.listenSocket = new ServerSocket(this.clientPort);
        this.clientKeys = new HashMap<>();
        this.clientOccurs = new Boolean(false);
        this.encryptHelper = new EncryptHelper();
        this.clientKeys = encryptHelper.readPublicKeys();
        this.start();
    }

    /**
     * run the thread class
     */
    public void run() {
        try {
            listen();
        } catch (IOException e) {
            System.out.println("Socket listen exception");
        }
    }

    /**
     * listen for tcp connections
     * @throws IOException
     */
    public void listen() throws IOException {
        while(true) {
            synchronized (clientlock){
                if (!clientOccurs){
                    Socket clientSocket = listenSocket.accept();
                    System.out.println("Server connected with a client: " + clientSocket.getInetAddress().toString() + " and " + clientSocket.getPort());
                    new ClientHandshakeTask(clientSocket);
                }
            }
        }
    }

    /**
     * The class represents the task of initialing handshake with a client.
     */
    private class ClientHandshakeTask extends Thread{
        private BufferedWriter out;
        private BufferedReader in;
        private Socket clientSocket;

        /**
         * The constructor of Client.
         * @param aClientSocket A tcp socket.
         */
        public ClientHandshakeTask(Socket aClientSocket) throws IOException{
            this.clientSocket = aClientSocket;
            this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            this.out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8));
            synchronized (clientlock){
                clientOccurs = true;
            }
            this.start();
        }
        /**
         * run the thread.
         */
        public void run(){
            System.out.println("Running one handshake task");
            try {
                String data = in.readLine();
                // socket has been closed
                if(data == null){
                    close();
                    clientOccurs = false;
                    return;
                }
                System.out.println("Im waiting for handshake, receive data: " + data);
                handshakeHandler(data);
                System.out.println("handshake finish");
                return;
            } catch (IOException e) {
                System.out.println("Connection lost by: " + e.toString());
                close();
                clientOccurs = false;
                return;
            }
        }

        /**
         * handle the handshake
         * @param str A string of data.
         */
        private void handshakeHandler(String str) throws IOException {
            Protocol protocol = ProtocolHelper.convertProtocol(str);
            // receive invalid protocol
            if(protocol == null){
                handleInvalidProtocol();
            }
            // receive handshake response
            else if(protocol instanceof ClientConnectionRequest){
                handleClientConnectionRequest((ClientConnectionRequest) protocol);
            }
            // other protocol type
            else{
                handleInvalidProtocol();
            }
        }

        /**
         * handle the invalid protocol received
         */
        private void handleInvalidProtocol() {
            send((new InvalidProtocol("Invalid Protocol when handshaking")).toString());
            close();
        }

        /**
         * handle the client connection request protocol
         * @param ccrq a clent connection request protocol.
         */
        private void handleClientConnectionRequest(ClientConnectionRequest ccrq){
            String identity = ccrq.identity;
            if (clientKeys.containsKey(identity)){
                PublicKey clientPublic = clientKeys.get(identity);
                String secretKeyString = encryptHelper.generateAESkey();
                new ClientConnection(clientSocket, in, out, secretKeyString, clientPublic);
            }
            else{
                ClientConnectionResponse ccrp = new ClientConnectionResponse();
                send(ccrp.toString());
                close();
                clientOccurs = false;
            }
        }

        /**
         * sent the data to the client
         * @param data A string of data.
         */
        public void send(String data){
            try {
                out.write(data + "\n");
                out.flush();
                System.out.println("I send to client: " + data);
            } catch (IOException e) {
                close();
                System.out.println("close socket exception");
            }
        }

        /**
         * close the connetion
         */
        private void close(){
            try {
                this.clientSocket.close();
            } catch (IOException e) {
                System.out.println("close socket exception");
            }
        }
    }

    /**
     * The class represents the task of connection with a client.
     */
    private class ClientConnection extends Thread {

        private BufferedReader in;
        private BufferedWriter out;
        private Socket clientSocket;
        private String secretKey;
        private String encryptedSecretKey;
        /**
         * The class represents the task of initialing handshake with a client.
         * @param aClientSocket the client socket
         * @param in the buffered reader in
         * @param out the buffered writer out
         * @param secretKey the secretKey for encrypt
         * @param clientPublic the public key of the client
         */
        public ClientConnection(Socket aClientSocket, BufferedReader in, BufferedWriter out, String secretKey, PublicKey clientPublic){
            this.clientSocket = aClientSocket;
            this.in = in;
            this.out = out;
            this.secretKey = secretKey;
            encryptedSecretKey = encryptHelper.sshEncrypt(secretKey, clientPublic);
            ClientConnectionResponse successResponse = new ClientConnectionResponse(encryptedSecretKey);
            send(successResponse.toString());
            this.start();
        }

        /**
         * sent some message to the client
         * @param data the data to be sent
         */
        public void send(String data) {
            try {
                out.write(data + "\n");
                out.flush();
                System.out.println("I send to a client: " + data);
            } catch (IOException e) {
                close();
                System.out.println("send message exception");
            }
        }

        public void sendEncrypt(String data, String secretKey){
            String encrypted = encryptHelper.aesEncrypt(data, secretKey);
            Document doc = new Document();
            doc.append("payload", encrypted);
            send(doc.toJson());
        }

        /**
         * run the thread.
         */
        public void run() {
            System.out.println("Running one client connection task");
            while(true) {
                try {
                    // receive
                    String data = in.readLine();
                    if (data == null){
                        System.out.println("check message error");
                        break;
                    }
                    Document datadoc = Document.parse(data);
                    if (!(datadoc.containsKey("payload") && datadoc.get("payload") instanceof String)){
                        send(encryptHelper.aesEncrypt((new InvalidProtocol("the protocol is invalid")).toString(), secretKey));
                        return;
                    }
                    String content = datadoc.getString("payload");
                    String decryptedMessage = encryptHelper.aesDecrypt(content, secretKey);
                    System.out.println("Decrypted message: "+decryptedMessage);
                    Protocol protocol = ProtocolHelper.convertProtocol(decryptedMessage);
                    if (protocol == null){
                       sendEncrypt((new InvalidProtocol("the protocol is invalid").toString()), secretKey);
                        break;
                    }
                    else if (protocol instanceof ClientConnectionRequest){
                        send((new ClientConnectionResponse(encryptedSecretKey)).toString());
                    }
                    else if (protocol instanceof ListPeersRequest){
                        ArrayList<ConnectedPeer> peers = communicationModule.getConnectedPeers();
                        sendEncrypt((new ListPeersResponse(peers)).toString(), secretKey);
                    }
                    else if (protocol instanceof ConnectPeerRequest){
                        System.out.println("Receive connect peer request.");
                        ConnectPeerRequest cpr = (ConnectPeerRequest) protocol;
                        String host = cpr.host;
                        int port = cpr.port;
                        ConnectedPeer peer= new ConnectedPeer(host, port);
                        communicationModule.initHandshake(peer, out, secretKey);
                    }
                    else if (protocol instanceof DisconnectPeerRequest){
                        DisconnectPeerRequest dprq = (DisconnectPeerRequest) protocol;
                        String host = dprq.host;
                        int port = dprq.port;
                        ConnectedPeer peer = new ConnectedPeer(host, port);
                        boolean success = communicationModule.disconnectPeer(peer);
                        DisconnectPeerResponse dprp;
                        if (success){
                            dprp = new DisconnectPeerResponse(host, port, true);
                        }
                        else{
                            dprp = new DisconnectPeerResponse(host, port, false);
                        }
                        sendEncrypt(dprp.toString(),secretKey);
                        Document doc = new Document();
                        doc.append("payload", encryptHelper.aesEncrypt(dprp.toString(), secretKey));
                        send(doc.toJson());
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
         * close the connection
         */
        private void close(){
            System.out.println("A Client connection closed");
            try {
                this.clientSocket.close();
                synchronized (clientlock){
                    clientOccurs = false;
                }
            } catch (IOException e) {
                System.out.println("CLose socket exception");
            }
        }
    }
}
