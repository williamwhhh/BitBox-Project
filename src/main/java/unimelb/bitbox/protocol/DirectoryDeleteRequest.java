package unimelb.bitbox.protocol;

import unimelb.bitbox.util.Document;

import java.util.Date;

/**
 * Class for DirectoryDeleteRequest protocol
 */
public class DirectoryDeleteRequest implements Protocol {

    public String pathname;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public DirectoryDeleteRequest(String pathname){
        this.pathname = pathname;
    }

    @Override
    public String toString() {
        Document doc = new Document();
        doc.append("command", "DIRECTORY_DELETE_REQUEST");
        doc.append("pathName", pathname);
        return doc.toJson();
    }

    public static DirectoryDeleteRequest convert(Document doc){
        if (doc.containsKey("pathName") && doc.get("pathName") instanceof String){
            return new DirectoryDeleteRequest(doc.getString("pathName"));
        }
        return null;
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
        if(p instanceof DirectoryDeleteResponse && ((DirectoryDeleteResponse) p).pathname.equals(this.pathname)){
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
