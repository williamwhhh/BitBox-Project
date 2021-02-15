package unimelb.bitbox.protocol;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;

import java.util.Date;

/**
 * Class for FileCreateRequest protocol
 */
public class FileCreateRequest implements Protocol {

    public Document fileDescriptor;
    public String pathName;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public FileCreateRequest(Document fileDescriptor, String pathName){
        this.fileDescriptor = fileDescriptor;
        this.pathName = pathName;
    }

    @Override
    public String toString(){
        Document doc = new Document();

        doc.append("command", "FILE_CREATE_REQUEST");
        doc.append("fileDescriptor", fileDescriptor);
        doc.append("pathName", pathName);

        return doc.toJson();
    }

    public static FileCreateRequest convert(Document doc){
        try{
            Document fileDescriptor = (Document) doc.get("fileDescriptor");
            String pathname = doc.getString("pathName");
            if (fileDescriptor != null && pathname != null){
                return new FileCreateRequest(fileDescriptor, pathname);
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
        return true;
    }

    @Override
    public long getCreatedTime() {
        return this.createTime;
    }

    @Override
    public boolean pairTo(Protocol p) {
        if(p instanceof FileCreateResponse && ((FileCreateResponse) p).pathName.equals(this.pathName)){
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
