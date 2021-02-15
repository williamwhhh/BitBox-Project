package unimelb.bitbox.protocol;

import unimelb.bitbox.ConnectedPeer;
import unimelb.bitbox.util.Document;

import java.util.Date;

/**
 * Class for HandshakeResponse protocol
 */
public class HandshakeResponse implements Protocol{
    public ConnectedPeer peer;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();
    public HandshakeResponse(ConnectedPeer peer){
        this.peer = peer;
    }

    @Override
    public String toString() {
        Document doc = new Document();
        doc.append( "command", "HANDSHAKE_RESPONSE");
        doc.append("hostPort", peer.toDoc());
        return doc.toJson();
    }

    public static HandshakeResponse convert(Document doc) {
        try{
            Document hostPort = (Document)doc.get("hostPort");
            String host = hostPort.getString("host");
            int port = (int) hostPort.getLong("port");
            if(hostPort != null && host != null){
                return new HandshakeResponse(new ConnectedPeer(host, port));
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
