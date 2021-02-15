package unimelb.bitbox.protocol;

import unimelb.bitbox.ConnectedPeer;
import unimelb.bitbox.util.Document;

import java.util.ArrayList;
import java.util.Date;

/**
 * Class for ConnectionRefused protocol
 */
public class ConnectionRefused implements Protocol {
    public ArrayList<ConnectedPeer> peers;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public ConnectionRefused(ArrayList<ConnectedPeer> peers){
        this.peers = new ArrayList<>(peers);
    }

    @Override
    public String toString() {
        Document doc = new Document();
        doc.append("command","CONNECTION_REFUSED");
        doc.append("message", "connection limit reached");
        ArrayList<Document> peerDocs = new ArrayList<>();
        for (ConnectedPeer peer:peers){
            peerDocs.add(peer.toDoc());
        }
        doc.append("peers",peerDocs);
        return doc.toJson();
    }

    public static ConnectionRefused convert(Document doc) {
        try{
            if(doc.get("peers") != null && doc.get("message") != null) {
                ArrayList<ConnectedPeer> peers = new ArrayList<>();
                for(Document peer: (ArrayList<Document>) doc.get("peers")){
                    peers.add(new ConnectedPeer(peer.getString("host"), (int) peer.getLong("port")));
                }
                return new ConnectionRefused(peers);
            }
            else {
                return null;
            }
        } catch (Exception e){
            return null;
        }
    }

    @Override
    public boolean isRequest() {
        return false;
    }

    @Override
    public long getCreatedTime() {
        return this.createTime;
    }

    @Override
    public boolean pairTo(Protocol p) {
        if(p instanceof HandshakeRequest){
            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public int getRetry() {
        return this.retryNum;
    }

    @Override
    public void addRetry() {
        this.retryNum += 1;
    }

    @Override
    public void updateCreatedTime() {
        this.createTime = (new Date()).getTime();
    }
}
