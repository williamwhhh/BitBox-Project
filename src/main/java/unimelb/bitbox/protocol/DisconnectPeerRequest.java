package unimelb.bitbox.protocol;

import unimelb.bitbox.util.Document;

import java.util.Date;

/**
 * Class for DisconnectPeerRequest protocol
 */
public class DisconnectPeerRequest implements Protocol{

    public String host;
    public int port;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public DisconnectPeerRequest(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public String toString() {
        Document doc = new Document();
        doc.append("command", "DISCONNECT_PEER_REQUEST");
        doc.append("host", host);
        doc.append("port", port);
        return doc.toJson();
    }

    public static DisconnectPeerRequest convert(Document doc){
        if (doc.containsKey("host") && doc.get("host") instanceof String &&
            doc.containsKey("port") && doc.get("port") instanceof Long) {
            return new DisconnectPeerRequest(doc.getString("host"), (int) doc.getLong("port"));
        } else{
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
        return false;
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
