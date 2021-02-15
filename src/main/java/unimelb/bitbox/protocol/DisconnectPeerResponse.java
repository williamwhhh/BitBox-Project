package unimelb.bitbox.protocol;

import unimelb.bitbox.util.Document;

import java.util.Date;

/**
 * Class for DisconnectPeerResponse protocol
 */
public class DisconnectPeerResponse implements Protocol{

    public String host;
    public int port;
    public boolean status;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public DisconnectPeerResponse(String host, int port, boolean status) {
        this.host = host;
        this.port = port;
        this.status = status;
    }

    @Override
    public String toString() {
        Document doc = new Document();
        doc.append("command", "DISCONNECT_PEER_RESPONSE");
        doc.append("host", host);
        doc.append("port", port);
        doc.append("status", status);
        if (status == true){
            doc.append("message", "disconnected from peer");
        } else{
            doc.append("message", "connection not active");
        }
        return doc.toJson();
    }

    public static DisconnectPeerResponse convert(Document doc){
        if (doc.containsKey("host") && doc.get("host") instanceof String &&
                doc.containsKey("port") && doc.get("port") instanceof Long &&
                doc.containsKey("status") && doc.get("status") instanceof Boolean &&
                doc.containsKey("message") && doc.get("message") instanceof String) {
            return new DisconnectPeerResponse(doc.getString("host"), (int) doc.getLong("port"),
                    doc.getBoolean("status"));
        } else{
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
