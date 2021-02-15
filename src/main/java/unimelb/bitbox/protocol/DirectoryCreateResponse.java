package unimelb.bitbox.protocol;

import unimelb.bitbox.util.Document;

import java.util.Date;

/**
 * Class for DirectoryCreateResponse protocol
 */
public class DirectoryCreateResponse implements Protocol{

    public String pathName;
    private String message;
    private Boolean status;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public DirectoryCreateResponse(String pathName, String errorMsg) {
        this.pathName = pathName;
        this.message = errorMsg;
        this.status = false;
    }

    public DirectoryCreateResponse(String pathName) {
        this.pathName = pathName;
        this.message = "directory created";
        this.status = true;
    }

    @Override
    public String toString() {
        Document doc = new Document();
        doc.append("command", "DIRECTORY_CREATE_RESPONSE");
        doc.append("pathName", pathName);
        doc.append("message", message);
        doc.append("status", status);

        return doc.toJson();

    }

    public static DirectoryCreateResponse convert(Document doc){
        if (doc.containsKey("pathName") && doc.get("pathName") instanceof String &&
                doc.containsKey("message") && doc.get("message") instanceof String &&
                doc.containsKey("status") && doc.get("status") instanceof Boolean){
            if (doc.getBoolean("status")){
                return new DirectoryCreateResponse(doc.getString("pathName"));
            }else {
                return new DirectoryCreateResponse(doc.getString("pathName"), doc.getString("message"));
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
        if(p instanceof DirectoryCreateRequest && ((DirectoryCreateRequest) p).pathName.equals(this.pathName)){
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
