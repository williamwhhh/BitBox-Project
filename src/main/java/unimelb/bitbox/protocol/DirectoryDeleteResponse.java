package unimelb.bitbox.protocol;

import unimelb.bitbox.util.Document;

import java.util.Date;

/**
 * Class for DirectoryDeleteResponse protocol
 */
public class DirectoryDeleteResponse implements Protocol {

    public String pathname;
    private boolean status;
    private String message;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public DirectoryDeleteResponse(String pathname, String errormsg) {
        this.pathname = pathname;
        this.status = false;
        this.message = errormsg;
    }

    public DirectoryDeleteResponse(String pathname) {
        this.pathname = pathname;
        this.status = true;
        this.message = "directory deleted";
    }

    @Override
    public String toString() {
        Document doc = new Document();
        doc.append("command", "DIRECTORY_DELETE_RESPONSE");
        doc.append("pathName", pathname);
        doc.append("message", message);
        doc.append("status", status);

        return doc.toJson();
    }

    public static DirectoryDeleteResponse convert(Document doc){
        if (doc.containsKey("pathName") && doc.get("pathName") instanceof String &&
                doc.containsKey("message") && doc.get("message") instanceof String &&
                doc.containsKey("status") && doc.get("status") instanceof Boolean){
            if (doc.getBoolean("status")){
                return new DirectoryDeleteResponse(doc.getString("pathName"));
            }else {
                return new DirectoryDeleteResponse(doc.getString("pathName"), doc.getString("message"));
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
        if(p instanceof DirectoryCreateRequest && ((DirectoryCreateRequest) p).pathName.equals(this.pathname)){
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
