package unimelb.bitbox.protocol;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;

import java.util.Date;

/**
 * Class for FileDeleteResponse protocol
 */
public class FileDeleteResponse implements Protocol {

    public Document fileDescriptor;
    public String pathName;
    private String message;
    private Boolean status;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public FileDeleteResponse(Document fileDescriptor, String pathName){
        this.fileDescriptor = fileDescriptor;
        this.pathName = pathName;
        this.message = "file deleted";
        this.status = true;
    }

    public FileDeleteResponse(Document des, String pathName, String message){
        this.fileDescriptor = des;
        this.pathName = pathName;
        this.message = message;
        this.status = false;
    }

    @Override
    public String toString(){
        Document doc = new Document();

        doc.append("command", "FILE_DELETE_RESPONSE");
        doc.append("fileDescriptor", fileDescriptor);
        doc.append("pathName", pathName);
        doc.append("message", message);
        doc.append("status", status);

        return doc.toJson();
    }

    public static FileDeleteResponse convert(Document doc){
        try{
            Document fileDescriptor = (Document) doc.get("fileDescriptor");
            String pathname = doc.getString("pathName");
            String message = doc.getString("message");
            Boolean status = doc.getBoolean("status");
            long lastModified = fileDescriptor.getLong("lastModified");
            long fileSize = fileDescriptor.getLong("fileSize");
            String md5 = fileDescriptor.getString("md5");
            if (fileDescriptor != null && pathname != null && message != null && md5 != null){
                if (status) {
                    return new FileDeleteResponse(fileDescriptor, pathname);
                }
                else{
                    return new FileDeleteResponse(fileDescriptor, pathname, message);
                }
            }
            else{
                return null;
            }
        }catch(Exception e){
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
