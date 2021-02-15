package unimelb.bitbox.protocol;

import unimelb.bitbox.util.Document;

import java.util.Date;

/**
 * Class for FileModifyRequest protocol
 */
public class FileModifyRequest implements Protocol{

    public String pathName;
    public Document fileDescriptor;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public FileModifyRequest(Document fileDescriptor, String pathName) {
        this.fileDescriptor = fileDescriptor;
        this.pathName = pathName;

    }

    public String toString() {
        Document doc = new Document();
        doc.append("command", "FILE_MODIFY_REQUEST");
        doc.append("fileDescriptor", fileDescriptor);
        doc.append("pathName", pathName);

        return doc.toJson();

    }

    public static FileModifyRequest convert(Document doc) {
        try{
            Document fileDescriptor = (Document)doc.get("fileDescriptor");
            String pathName = doc.getString("pathName");
            String md5 = fileDescriptor.getString("md5");
            long lastModified = fileDescriptor.getLong("lastModified");
            long fileSize = fileDescriptor.getLong("fileSize");
            if(fileDescriptor != null && pathName != null && md5 != null) {
                Document fileDes = new Document();
                fileDes.append("md5", md5);
                fileDes.append("lastModified", lastModified);
                fileDes.append("fileSize", fileSize);
                return new FileModifyRequest(fileDes, pathName);
            }
            else {
                return null;
            }
        }
        catch (Exception e) {
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
        if(p instanceof FileModifyResponse && ((FileModifyResponse) p).pathName.equals(this.pathName)){
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
