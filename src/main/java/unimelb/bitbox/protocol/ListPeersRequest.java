package unimelb.bitbox.protocol;

import unimelb.bitbox.util.Document;

import java.util.Date;

/**
 * Class for ListPeersRequest protocol
 */
public class ListPeersRequest implements Protocol{

    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public ListPeersRequest() {
    }

    @Override
    public String toString() {
        Document doc = new Document();
        doc.append("command", "LIST_PEERS_REQUEST");
        return doc.toJson();
    }

    public static ListPeersRequest convert(Document doc){
        return new ListPeersRequest();
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
