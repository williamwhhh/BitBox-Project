package unimelb.bitbox.protocol;

import unimelb.bitbox.util.Document;

import java.util.Date;

/**
 * Class for ClientConnectionResponse protocol
 */
public class ClientConnectionResponse implements Protocol{

    public String encryptedKey;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public ClientConnectionResponse(String encryptedKey) { this.encryptedKey = encryptedKey; }
    public ClientConnectionResponse(){}

    @Override
    public String toString() {
        Document doc = new Document();
        doc.append("command", "AUTH_RESPONSE");
        if (encryptedKey != null){
            doc.append("AES128", encryptedKey);
            doc.append("status", true);
            doc.append("message","public key found");
        }else {
            doc.append("status", false);
            doc.append("message","public key not found");
        }

        return doc.toJson();
    }

    public static ClientConnectionResponse convert(Document doc){
        if (doc.containsKey("AES128") && doc.containsKey("status")) {
            if (doc.getBoolean("status") == true){
                return new ClientConnectionResponse(doc.getString("AES128"));
            }else {
                return null;
            }
        }else if (doc.containsKey("status")){
            if (doc.getBoolean("status") == false){
                return new ClientConnectionResponse(null);
            }else {
                return null;
            }
        }else {
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
