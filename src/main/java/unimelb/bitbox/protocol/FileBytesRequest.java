package unimelb.bitbox.protocol;

import unimelb.bitbox.util.Document;

import java.util.Date;

/**
 * Class for FileBytesRequest protocol
 */
public class FileBytesRequest implements Protocol {

    public Document fileDescriptor;
    public String pathname;
    public long position;
    public long length;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public FileBytesRequest(Document fileDescriptor, String pathname, long position, long length) {

        this.fileDescriptor = fileDescriptor;
        this.pathname = pathname;
        this.position = position;
        this.length = length;

    }

    public String toString() {

        Document doc = new Document();
        doc.append("command", "FILE_BYTES_REQUEST");
        doc.append("fileDescriptor", fileDescriptor);
        doc.append("pathName", pathname);
        doc.append("position", position);
        doc.append("length", length);

        return doc.toJson();

    }

    public static FileBytesRequest convert(Document doc){
        if (doc.containsKey("fileDescriptor") && doc.get("fileDescriptor") instanceof Document){
            Document fileDes = (Document) doc.get("fileDescriptor");
            if (fileDes.containsKey("md5") && fileDes.get("md5") instanceof String &&
                    fileDes.containsKey("lastModified") && fileDes.get("lastModified") instanceof Long &&
                    fileDes.containsKey("fileSize") && fileDes.get("fileSize") instanceof Long){
                if (doc.containsKey("pathName") && doc.get("pathName") instanceof String &&
                        doc.containsKey("position") && doc.get("position") instanceof Long &&
                        doc.containsKey("length") && doc.get("length") instanceof Long){
                    return new FileBytesRequest(fileDes, doc.getString("pathName"),
                            doc.getLong("position"), doc.getLong("length"));
                }
            }
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
        if(p instanceof FileBytesResponse && ((FileBytesResponse) p).pathname.equals(this.pathname) && ((FileBytesResponse) p).position == this.position){
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
