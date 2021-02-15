package unimelb.bitbox.protocol;

import unimelb.bitbox.ConnectedPeer;
import unimelb.bitbox.util.Document;

import java.util.Date;

/**
 * Class for HandshakeRequest protocol
 */
public class HandshakeRequest implements Protocol {
    public ConnectedPeer peer;
    public HandshakeRequest(ConnectedPeer peer){
        this.peer = peer;
    }
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    @Override
    public String toString() {
        Document doc = new Document();
        doc.append( "command", "HANDSHAKE_REQUEST");
        doc.append("hostPort", peer.toDoc());
        return doc.toJson();
    }

    public static HandshakeRequest convert(Document doc) {
        try{
            Document hostPort = (Document)doc.get("hostPort");
            String host = hostPort.getString("host");
            int port = (int)hostPort.getLong("port");
            if(hostPort != null && host != null){
                return new HandshakeRequest(new ConnectedPeer(host, port));
            }
            else{
                return null;
            }
        }
        catch(Exception e){
            return null;
        }
    }

    @Override
    public boolean isRequest() {
        return true;
    }

    @Override
    public long getCreatedTime() {
        return this.createTime;
    }

    @Override
    public boolean pairTo(Protocol p) {
        if(p instanceof HandshakeResponse || p instanceof ConnectionRefused){
            return true;
        }
        else{
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
