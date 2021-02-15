package unimelb.bitbox.protocol;

import unimelb.bitbox.util.Document;

import java.util.Date;

/**
 * Class for FileModifyResponse protocol
 */
public class FileModifyResponse implements Protocol{

    public Document fileDescriptor;
    public String pathName;
    private String message;
    private Boolean status;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public FileModifyResponse(Document fileDescriptor, String pathName, String errorMsg) {
        this.fileDescriptor = fileDescriptor;
        this.pathName = pathName;
        this.message = errorMsg;
        this.status = false;

    }

    public FileModifyResponse(Document fileDescriptor, String pathName) {
        this.fileDescriptor = fileDescriptor;
        this.pathName = pathName;
        this.message = "file loader ready";
        this.status = true;

    }

    public String toString() {
        Document doc = new Document();
        doc.append("command", "FILE_MODIFY_RESPONSE");
        doc.append("fileDescriptor", fileDescriptor);
        doc.append("pathName", pathName);
        doc.append("message", message);
        doc.append("status", status);
        return doc.toJson();
    }

    public static FileModifyResponse convert(Document doc) {
        try{
            Document fileDes = (Document)doc.get("fileDescriptor");
            String pathName = doc.getString("pathName");
            String message = doc.getString("message");
            String md5 = fileDes.getString("md5");
            boolean status = doc.getBoolean("status");
            long lastModified = fileDes.getLong("lastModified");
            long fileSize = fileDes.getLong("fileSize");
            if(fileDes != null && pathName != null && message != null && md5 != null) {
                return new FileModifyResponse(fileDes, pathName);
            }
            else {
                return null;
            }
        }
        catch (Exception e){
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
        if(p instanceof FileDeleteRequest && ((FileDeleteRequest) p).pathName.equals(this.pathName)){
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
