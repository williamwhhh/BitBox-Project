package unimelb.bitbox.protocol;

import unimelb.bitbox.util.Document;

import java.util.Date;

/**
 * Class for ConnectPeerResponse protocol
 */
public class ConnectPeerResponse implements Protocol{

    public String host;
    public int port;
    public boolean status;
    public String message;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public ConnectPeerResponse(String host, int port) {
        this.host = host;
        this.port = port;
        this.status = true;
        this.message = "connected to peer";
    }
    public ConnectPeerResponse(String host, int port, boolean status, String message){
        this.host = host;
        this.port = port;
        this.status = status;
        this.message = message;
    }

    @Override
    public String toString() {
        Document doc = new Document();
        doc.append("command", "CONNECT_PEER_RESPONSE");
        doc.append("host", host);
        doc.append("port", port);
        doc.append("status", status);
        doc.append("message", message);
        return doc.toJson();
    }

    public static ConnectPeerResponse convert(Document doc){
        if (doc.containsKey("host") && doc.get("host") instanceof String &&
                doc.containsKey("port") && doc.get("port") instanceof Long &&
                doc.containsKey("status") && doc.get("status") instanceof Boolean &&
                doc.containsKey("message") && doc.get("message") instanceof String) {
            return new ConnectPeerResponse(doc.getString("host"), (int) doc.getLong("port"),
                    doc.getBoolean("status"), doc.getString("message"));
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
