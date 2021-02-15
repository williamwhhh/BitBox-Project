package unimelb.bitbox.protocol;

import unimelb.bitbox.util.Document;

import java.util.Date;

/**
 * Class for ClientConnectionRequest protocol
 */
public class ClientConnectionRequest implements Protocol{

    public String identity;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public ClientConnectionRequest(String identity) {
        this.identity = identity;
    }

    @Override
    public String toString() {
        Document doc = new Document();
        doc.append("command", "AUTH_REQUEST");
        doc.append("identity", identity);
        return doc.toJson();
    }

    public static ClientConnectionRequest convert(Document doc){
        if (doc.containsKey("identity")) {
            return new ClientConnectionRequest(doc.getString("identity"));
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
