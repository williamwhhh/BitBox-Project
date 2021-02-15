package unimelb.bitbox.protocol;

import unimelb.bitbox.util.Document;

import java.util.Date;

/**
 * Class for InvalidProtocol protocol
 */
public class InvalidProtocol implements Protocol {

    private String errorMsg;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public InvalidProtocol(String errorMsg){
        this.errorMsg = errorMsg;
    }

    @Override
    public String toString() {
        Document doc = new Document();
        doc.append("command","INVALID_PROTOCOL");
        doc.append("message", errorMsg);
        return doc.toJson();
    }

    public static InvalidProtocol convert(Document doc){
        try{
            String message = doc.getString("message");
            if(message != null){
                return new InvalidProtocol(message);
            }
            else {
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
