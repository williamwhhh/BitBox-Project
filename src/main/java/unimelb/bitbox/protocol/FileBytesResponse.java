package unimelb.bitbox.protocol;

import unimelb.bitbox.util.Document;

import java.util.Date;

/**
 * Class for FileBytesResponse protocol
 */
public class FileBytesResponse implements Protocol {

    public Document fileDescriptor;
    public String pathname;
    public long position;
    public long length;
    public String content;
    private String message;
    public boolean status;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public FileBytesResponse(Document fileDescriptor, String pathname, long position, long length, String content,
                             String errormsg) {

        this.fileDescriptor = fileDescriptor;
        this.pathname = pathname;
        this.position = position;
        this.length = length;
        this.content = content;
        this.status = false;
        this.message = errormsg;

    }

    public FileBytesResponse(Document fileDescriptor, String pathname, long position, long length, String content) {

        this.fileDescriptor = fileDescriptor;
        this.pathname = pathname;
        this.position = position;
        this.length = length;
        this.content = content;
        this.status = true;
        this.message = "successful read";

    }

    public String toString() {

        Document doc = new Document();
        doc.append("command", "FILE_BYTES_RESPONSE");
        doc.append("fileDescriptor", fileDescriptor);
        doc.append("pathName", pathname);
        doc.append("position", position);
        doc.append("length", length);
        doc.append("content", content);
        doc.append("message", message);
        doc.append("status", status);

        return doc.toJson();

    }

    public static FileBytesResponse convert(Document doc) {
        try {
            Document fileDescriptor = (Document) doc.get("fileDescriptor");
            String pathname = doc.getString("pathName");
            long position = doc.getLong("position");
            long length =  doc.getLong("length");
            String content = doc.getString("content");
            Boolean status = doc.getBoolean("status");
            String errormsg = doc.getString("message");
            if (fileDescriptor != null && pathname != null && content != null && errormsg != null && status != null) {
                if (status == true) {
                    return new FileBytesResponse(fileDescriptor, pathname, position, length, content);
                } else {
                    return new FileBytesResponse(fileDescriptor, pathname, position, length, content, errormsg);
                }
            }
            else{
                return null;
            }
        } catch (Exception e) {
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
        if(p instanceof FileBytesRequest && ((FileBytesRequest) p).pathname.equals(this.pathname) && ((FileBytesRequest) p).position == this.position){
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

