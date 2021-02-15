package unimelb.bitbox.protocol;

import unimelb.bitbox.util.Document;

import java.util.Date;

/**
 * Class for DirectoryCreateRequest protocol
 */
public class DirectoryCreateRequest implements Protocol{

    public String pathName;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public DirectoryCreateRequest(String pathName) {
        this.pathName = pathName;

    }

    @Override
    public String toString() {
        Document doc = new Document();
        doc.append("command", "DIRECTORY_CREATE_REQUEST");
        doc.append("pathName", pathName);

        return doc.toJson();

    }

    public static DirectoryCreateRequest convert(Document doc){
        if (doc.containsKey("pathName") && doc.get("pathName") instanceof String){
            return new DirectoryCreateRequest(doc.getString("pathName"));
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
        if(p instanceof DirectoryCreateResponse && ((DirectoryCreateResponse) p).pathName.equals(this.pathName)){
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
