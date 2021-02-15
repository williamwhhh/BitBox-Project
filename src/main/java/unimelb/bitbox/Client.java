package unimelb.bitbox;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import sun.misc.Request;
import unimelb.bitbox.protocol.*;
import unimelb.bitbox.util.Document;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;

/**
 * The client class, provide a controller for client
 */
public class Client {
    private EncryptHelper encryptHelper;
    public Client(){
        this.encryptHelper = new EncryptHelper();
    }
    public enum RequestType {ListPeer, ConnectPeer, DisconnectPeer};

    /**
     * The main that is used by the client side
     */
    public static void main(String[] args){
        Client client = new Client();
        CmdLineArgs argsBean = new CmdLineArgs();
        CmdLineParser parser = new CmdLineParser(argsBean);
        try {
            parser.parseArgument(args);
            String command =  argsBean.getCommand();
            String server = argsBean.getServer();
            String[] hostPort = server.split(":");
            String identity = argsBean.getIdentity();
            if (hostPort.length != 2){
                System.out.println("Invalid server address");
                return;
            }
            String host = hostPort[0];
            String port = hostPort[1];
            int portno;
            try{
                portno = Integer.parseInt(port);
            } catch (Exception e){
                System.out.println("Invalid server port");
                return;
            }
            if (command.equals("list_peers")){
                client.listPeerRequest(host, portno, identity);
            }
            else{
                String peer = argsBean.getPeer();
                if (peer == null){
                    System.out.println("Requires command line argument -p");
                    return;
                }
                String[] peerHostPort = peer.split(":");
                if (peerHostPort.length != 2){
                    System.out.println("Invalid peer address");
                    return;
                }
                else{
                    String peerHost = peerHostPort[0];
                    String peerPort = peerHostPort[1];
                    int peerPortno;
                    try{
                        peerPortno = Integer.parseInt(peerPort);
                    } catch (Exception e){
                        System.out.println("invalid peer port");
                        return;
                    }
                    if (command.equals("connect_peer")){
                        client.connectPeerRequest(host, portno, peerHost, peerPortno, identity);
                    }
                    else if (command.equals("disconnect_peer")){
                        client.disconnectPeerRequest(host, portno, peerHost, peerPortno, identity);

                    }
                    else{
                        System.out.println("Invalid command");
                    }
                }
            }
        } catch (CmdLineException e) {
            System.out.println("Command line exception");
            parser.printUsage(System.err);
        }
    }

    /**
     * initial a list peer request
     * @param host the host address of the peer
     * @param port the port number of the peer
     * @param identity the identity of the client
     */
    public void listPeerRequest(String host, int port, String identity){
        new ClientRequest(host, port, identity);
    }

    /**
     * initial a connect peer request
     * @param host the host address of the peer
     * @param port the port number of the peer
     * @param peerHost the host address of the peer to connect
     * @param peerPort the port number of the peer to connect
     * @param identity the identity of the client
     */
    public void connectPeerRequest(String host, int port, String peerHost, int peerPort, String identity){
        new ClientRequest(host, port, peerHost, peerPort, RequestType.ConnectPeer, identity);
    }

    /**
     * initial a disconnect peer request
     * @param host the host address of the peer
     * @param port the port number of the peer
     * @param peerHost the host address of the peer to disconnect
     * @param peerPort the port number of the peer to disconnect
     * @param identity the identity of the client
     */
    public void disconnectPeerRequest(String host, int port, String peerHost, int peerPort, String identity){
        new ClientRequest(host, port, peerHost, peerPort, RequestType.DisconnectPeer, identity);
    }

    public class ClientRequest extends Thread{
        private RequestType type;
        private String serverHost;
        private int serverPort;
        private String peerHost;
        private int peerPort;
        private int tryCount = 5;
        private String identity;
        public ClientRequest(String host, int port, String identity){
            this.type = RequestType.ListPeer;
            this.serverHost = host;
            this.serverPort = port;
            this.identity = identity;
            this.start();
        }
        public ClientRequest(String host, int port, String peerHost, int peerPort, RequestType type, String identity){
            this.type = type;
            this.serverHost = host;
            this.serverPort = port;
            this.peerHost = peerHost;
            this.peerPort = peerPort;
            this.identity = identity;
            this.start();
        }
        public void run(){
            while (tryCount > 0){
                try {
                    Socket s = new Socket(serverHost, serverPort);
                    System.out.println("Tcp connected");
                    if (type == RequestType.ListPeer){
                        new ClientHandshake(type, s, identity);
                    }
                    else{
                        new ClientHandshake(peerHost, peerPort, type, s, identity);
                    }
                    return;
                } catch (IOException e) {
                    tryCount -= 1;
                    System.out.println("Tcp connection failed. Remaining retry times: " + tryCount);
                    try {
                        this.sleep(5 * 1000);
                    } catch (InterruptedException e1) {
                        System.out.println("thread sleep exception");
                    }
                }
            }
        }
    }

    /**
     * The class represents the task of initialing handshake with a peer.
     */
    public class ClientHandshake extends Thread{
        private String peerHost;
        private int peerPort;
        private RequestType type;
        private Socket socket;
        private BufferedWriter out;
        private BufferedReader in;
        private String secretKey;
        private boolean handshaked;
        private String identity;

        /**
         * Constructor
         * @param type the type of the request
         * @param s the socket
         * @param peerHost the host address of the peer
         * @param peerPort the port number of the peer
         * @param identity the identity of the client
         * @throws IOException
         */
        public ClientHandshake(String peerHost, int peerPort, RequestType type, Socket s, String identity) throws IOException{
            this.peerHost = peerHost;
            this.peerPort = peerPort;
            this.type = type;
            this.socket = s;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            this.handshaked = false;
            this.identity = identity;
            this.sendConnectionRequest();
            this.start();
        }

        /**
         * Constructor
         * @param type the type of the request
         * @param s the socket
         * @param identity the identity of the client
         * @throws IOException
         */
        public ClientHandshake(RequestType type, Socket s, String identity) throws IOException{
            this.type = type;
            this.socket = s;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            this.handshaked = false;
            this.identity = identity;
            this.sendConnectionRequest();
            this.start();
        }

        /**
         * send connection request to the peer
         */
        public void sendConnectionRequest(){
            ClientConnectionRequest ccrq = new ClientConnectionRequest(identity);
            send(ccrq.toString());
        }

        /**
         * send data to the peer
         * @param data the data
         */
        public void send(String data){
            try {
                out.write(data + "\n");
                out.flush();
                System.out.println("I send: " + data);
            } catch (IOException e) {
                System.out.println("Tcp connection lost.");
                close();
            }
        }

        /**
         * close the connection
         */
        private void close(){
            try {
                this.socket.close();
                System.out.println("Socket closed!");
            } catch (IOException e) {
                System.out.println("Close socket exception");
            }
        }

        /**
         * run the thread
         */
        public void run(){
            try {
                while (true){
                    String data = in.readLine();
                    // socket has been closed
                    if(data == null){
                        System.out.println("Tcp connection lost.");
                        close();
                        return;
                    }
                    if (!handshaked){
                        Protocol p = ProtocolHelper.convertProtocol(data);
                        if (!(p instanceof ClientConnectionResponse)){
                            System.out.println("No client connection response received.");
                            return;
                        }
                        else{
                            System.out.println("Client connection succeeded. ");
                            ClientConnectionResponse ccrp = (ClientConnectionResponse) p;
                            String encryptedKey = ccrp.encryptedKey;
                            if (encryptedKey == null){
                                System.out.println(ccrp.toString());
                                close();
                                return;
                            }
                            PrivateKey privateKey = encryptHelper.getPrivateKey();
                            this.secretKey = encryptHelper.sshDecrypt(encryptedKey, privateKey);
                            this.handshaked = true;
                            Document encrypted = new Document();
                            if (this.type == RequestType.ListPeer){
                                ListPeersRequest lprq = new ListPeersRequest();
                                encrypted.append("payload", encryptHelper.aesEncrypt(lprq.toString(), secretKey));
//                                encrypted.append("command","LIST_PEERS_REQUEST");
                            }
                            else if (this.type == RequestType.ConnectPeer){
                                ConnectPeerRequest cprq = new ConnectPeerRequest(this.peerHost, this.peerPort);
                                encrypted.append("payload", encryptHelper.aesEncrypt(cprq.toString(), secretKey));
//                                encrypted.append("command", "CONNECT_PEER_REQUEST");
//                                encrypted.append("host", this.peerHost);
//                                encrypted.append("port", this.peerPort);
                            }
                            else{
                                DisconnectPeerRequest dprq = new DisconnectPeerRequest(this.peerHost, this.peerPort);
                                encrypted.append("payload", encryptHelper.aesEncrypt(dprq.toString(), secretKey));
//                                encrypted.append("command","DISCONNECT_PEER_REQUEST");
//                                encrypted.append("host",this.peerHost);
//                                encrypted.append("port", this.peerPort);
                            }
                            send(encrypted.toJson());
                        }
                    }
                    else{
                        try{
                            Document dataDoc = Document.parse(data);
                            String content = dataDoc.getString("payload");
                            String decryptedMessage = encryptHelper.aesDecrypt(content, secretKey);
                            System.out.println("I received:" + decryptedMessage);
                            Protocol p = ProtocolHelper.convertProtocol(decryptedMessage);
                            if (p instanceof ConnectPeerResponse){
                                ConnectPeerResponse cprp = (ConnectPeerResponse) p;
                                Boolean status = cprp.status;
                                String phost = cprp.host;
                                int port = cprp.port;
                                String output;
                                if (status){
                                    output = "Successfully connected to";
                                }
                                else{
                                    output = "Fail to connect to";
                                }
                                output = output + " peer: " + phost + ":" + port;
                                System.out.println(output);
                            }
                            else if (p instanceof DisconnectPeerResponse){
                                DisconnectPeerResponse dprp = (DisconnectPeerResponse) p;
                                Boolean status = dprp.status;
                                String phost = dprp.host;
                                int port = dprp.port;
                                String output;
                                if (status){
                                    output = "Successfully disconnected from peer: " + phost + ":" + port;
                                }
                                else{
                                    output = "peer: " + phost + ":" + port + " is not connected";
                                }
                                System.out.println(output);
                            }
                            else if (p instanceof ListPeersResponse){
                                ListPeersResponse lprp = (ListPeersResponse) p;
                                System.out.println("List of connected peers: {");
                                for (ConnectedPeer peer:lprp.peers){
                                    System.out.println("  " + peer.host + ":" +peer.port);
                                }
                                System.out.println("}");
                            }
                            else{
                                System.out.println("fuck you!!");
                            }
                        }
                        catch (Exception e){
                            System.out.println("Can not decrypted message: " + data);
                        }
                        return;
                    }
                }
            } catch (IOException e) {
                System.out.println("Connection lost by: " + e.toString());
                close();
                return;
            }
        }
    }

}
