package unimelb.bitbox;

import unimelb.bitbox.protocol.*;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;

public class RequestHandler {

    private FileSystemManager fileSystemManager;

    /**
     * Constructor.
     * @param fileSystemManager The user's file system manager.
     */
    public RequestHandler(FileSystemManager fileSystemManager) {
        this.fileSystemManager = fileSystemManager;
    }

    /**
     * Designed to handle any input string other than connection related request/response. The method checks the
     * validity of the input string and do the corresponding operations on the file system. A list of protocols are then
     * generated based on the input message and the status of operations.
     * @param requestStr The input string that the user received.
     * @return A list of protocols to be sent to the other peer. The list is empty if no subsequent response is needed.
     */
    public ArrayList<Protocol> handleRequest(String requestStr) {
        ArrayList<Protocol> responses = new ArrayList<>();

        // Check if the input protocol is valid
        Protocol protocol = ProtocolHelper.convertProtocol(requestStr);

        // Invalid protocol
        if (protocol == null) {
            responses.add(new InvalidProtocol("the protocol is invalid"));
        }

        // Directory
        else if (protocol instanceof DirectoryCreateRequest) {

            String pathName = ((DirectoryCreateRequest) protocol).pathName;
            DirectoryCreateHelper directoryCreateHelper = new DirectoryCreateHelper(fileSystemManager, pathName);

            if (directoryCreateHelper.checkName()) {
                directoryCreateHelper.doOperation();
            }
            responses.add(directoryCreateHelper.getResponse());
        }

        else if (protocol instanceof DirectoryDeleteRequest) {

            String pathName = ((DirectoryDeleteRequest) protocol).pathname;
            DirectoryDeleteHelper directoryDeleteHelper = new DirectoryDeleteHelper(fileSystemManager, pathName);

            if (directoryDeleteHelper.checkName()) {
                directoryDeleteHelper.doOperation();
            }
            responses.add(directoryDeleteHelper.getResponse());
        }

        // File
        else if (protocol instanceof FileCreateRequest) {

            String pathName = ((FileCreateRequest) protocol).pathName;
            Document fileDescriptor = ((FileCreateRequest) protocol).fileDescriptor;
            FileCreateHelper fileCreateHelper = new FileCreateHelper(fileSystemManager, pathName, fileDescriptor);

            if (fileCreateHelper.checkName()) {
                fileCreateHelper.doOperation();
            }
            responses.add(fileCreateHelper.getResponse());
            if (fileCreateHelper.isFileBytesReqPending()) {
                responses.add(fileCreateHelper.getFileBytesRequest());
            }
        }

        else if (protocol instanceof FileDeleteRequest) {
            String pathName = ((FileDeleteRequest) protocol).pathName;
            Document fileDescriptor = ((FileDeleteRequest) protocol).fileDescriptor;
            FileDeleteHelper fileDeleteHelper = new FileDeleteHelper(fileSystemManager, pathName, fileDescriptor);

            if (fileDeleteHelper.checkName()) {
                fileDeleteHelper.doOperation();
            }
            responses.add(fileDeleteHelper.getResponse());
        }

        else if (protocol instanceof FileModifyRequest) {
            String pathName = ((FileModifyRequest) protocol).pathName;
            Document fileDescriptor = ((FileModifyRequest) protocol).fileDescriptor;
            FileModifyHelper fileModifyHelper = new FileModifyHelper(fileSystemManager, pathName, fileDescriptor);

            if (fileModifyHelper.checkName()) {
                fileModifyHelper.doOperation();
            }
            responses.add(fileModifyHelper.getResponse());
            if (fileModifyHelper.isFileBytesReqPending()) {
                responses.add(fileModifyHelper.getFileBytesRequest());
            }
        }

        // File bytes
        else if (protocol instanceof FileBytesRequest) {
            FileBytesRequestHelper fileBytesRequestHelper = new FileBytesRequestHelper(fileSystemManager, protocol);
            if (fileBytesRequestHelper.checkName()) {
                fileBytesRequestHelper.doOperation();
            }
            responses.add(fileBytesRequestHelper.getResponse());
        }

        else if (protocol instanceof FileBytesResponse) {
            FileBytesResponseHelper fileBytesResponseHelper = new FileBytesResponseHelper(fileSystemManager, protocol);
            if (fileBytesResponseHelper.checkName()) {
                if (fileBytesResponseHelper.doOperation()) {
                    responses.add(fileBytesResponseHelper.getResponse());
                }
            }
        }

        return responses;
    }


    ////////////////////
    // Internals
    ////////////////////

    /**
     * Following are internal helper classes that assist the requestHandler method.
     */

    // Helpers for directory related operations
    private class DirectoryCreateHelper{
        private FileSystemManager fileSystemManager;
        private String pathName;
        private String message;
        private Protocol response;

        public DirectoryCreateHelper(FileSystemManager fileSystemManager, String pathName) {
            this.fileSystemManager = fileSystemManager;
            this.pathName = pathName;
        }

        public Boolean checkName() {
            if (!fileSystemManager.isSafePathName(pathName)) {
                message = "unsafe pathname given";
                response = new DirectoryCreateResponse(pathName, message);
                return false;
            }
            else if (fileSystemManager.dirNameExists(pathName)) {
                message = "pathname already exists";
                response = new DirectoryCreateResponse(pathName, message);
                return false;
            }
            else return true;
        }

        public void doOperation() {
            if (fileSystemManager.makeDirectory(pathName)) {
                response = new DirectoryCreateResponse(pathName);
            }
            else {
                message = "there was a problem creating the directory";
                response = new DirectoryCreateResponse(pathName, message);
            }
        }

        public Protocol getResponse() { return response; }
    }

    private class DirectoryDeleteHelper {
        private FileSystemManager fileSystemManager;
        private String pathName;
        private String message;
        private Protocol response;

        public DirectoryDeleteHelper(FileSystemManager fileSystemManager, String pathName) {
            this.fileSystemManager = fileSystemManager;
            this.pathName = pathName;
        }

        public Boolean checkName() {
            if (!fileSystemManager.isSafePathName(pathName)) {
                message = "unsafe pathname given";
                response = new DirectoryDeleteResponse(pathName, message);
                return false;
            }
            else if (!fileSystemManager.dirNameExists(pathName)) {
                message = "pathname does not exist";
                response = new DirectoryDeleteResponse(pathName, message);
                return false;
            }
            else return true;
        }

        public void doOperation() {
            if (fileSystemManager.deleteDirectory(pathName)) {
                response = new DirectoryDeleteResponse(pathName);
            }
            else {
                message = "there was a problem deleting the directory";
                response = new DirectoryDeleteResponse(pathName, message);
            }
        }

        public Protocol getResponse() { return response; }
    }

    // Helpers for file related operations
    private class FileCreateHelper {
        private FileSystemManager fileSystemManager;
        private String pathName;
        private Document fileDescriptor;
        private Protocol response;
        private String message;
        private Protocol fileBytesRequest;
        private Boolean fileBytesReqPending = false;
        private Boolean createModifyFl = false;

        public FileCreateHelper(FileSystemManager fileSystemManager, String pathName, Document fileDescriptor) {
            this.fileSystemManager = fileSystemManager;
            this.pathName = pathName;
            this.fileDescriptor = fileDescriptor;
        }

        public Boolean checkName() {

            String md5 = fileDescriptor.getString("md5");

            if (!fileSystemManager.isSafePathName(pathName)) {
                message = "unsafe pathname given";
                response = new FileCreateResponse(fileDescriptor, pathName, message);
                return false;
            }

            if (fileSystemManager.fileNameExists(pathName, md5)) {
                // check file name and content
                message = "pathname already exists";
                response = new FileCreateResponse(fileDescriptor, pathName, message);
                return false;
            }

            if (fileSystemManager.fileNameExists(pathName) && !fileSystemManager.fileNameExists(pathName, md5)) {
                createModifyFl = true;
                return true;
            }
            return true;
        }

        public void doOperation() {

            String md5 = fileDescriptor.getString("md5");
            long lastModified = fileDescriptor.getLong("lastModified");
            long fileSize = fileDescriptor.getLong("fileSize");

            long position = 0;
            long length;
            long blockSize = Math.min(Long.parseLong(Configuration.getConfigurationValue("blockSize")), (long) 8192);

            // Determine the length of data to be request
            if (fileSize >= blockSize) {
                length = blockSize;
            } else {length = fileSize;}

            try{
                if (!createModifyFl) {
                    if (fileSystemManager.createFileLoader(pathName, md5, fileSize, lastModified)) {
                        try{
                            if (!fileSystemManager.checkShortcut(pathName)) {
                                // no local copy exists; need to send File Bytes Request
                                fileBytesRequest = new FileBytesRequest(fileDescriptor, pathName, position, length);
                                fileBytesReqPending = true;
                            }
                            response = new FileCreateResponse(fileDescriptor, pathName);
                        }
                        catch (NoSuchAlgorithmException | IOException e) {
                            // do not know if local copy exists, so send File Bytes Request
                            System.out.println("Problem in IO");
                            fileBytesRequest = new FileBytesRequest(fileDescriptor, pathName, position, length);
                            fileBytesReqPending = true;
                            response = new FileCreateResponse(fileDescriptor, pathName);
                        }
                    }
                    else {
                        // fail to create file loader
                        message = "there was a problem creating the file";
                        response = new FileCreateResponse(fileDescriptor, pathName, message);
                    }
                }
                else {
                    if (fileSystemManager.modifyFileLoader(pathName, md5, lastModified)) {
                        try{
                            if (!fileSystemManager.checkShortcut(pathName)) {
                                // no local copy exists; need to send File Bytes Request
                                fileBytesRequest = new FileBytesRequest(fileDescriptor, pathName, position, length);
                                fileBytesReqPending = true;
                            }
                            response = new FileCreateResponse(fileDescriptor, pathName);
                        }
                        catch (NoSuchAlgorithmException | IOException e) {
                            // do not know if local copy exists, so send File Bytes Request
                            fileBytesRequest = new FileBytesRequest(fileDescriptor, pathName, position, length);
                            fileBytesReqPending = true;
                            response = new FileCreateResponse(fileDescriptor, pathName);
                        }
                    }
                    else {
                        // fail to modify file loader
                        message = "there was a problem creating the file";
                        response = new FileCreateResponse(fileDescriptor, pathName, message);
                    }
                }
            }
            catch (NoSuchAlgorithmException | IOException e) {
                message = "there was a problem creating the file";
                response = new FileCreateResponse(fileDescriptor, pathName, message);
            }
        }

        public Protocol getResponse() { return response; }

        public Boolean isFileBytesReqPending() { return fileBytesReqPending; }

        public Protocol getFileBytesRequest() { return fileBytesRequest; }

    }

    private class FileDeleteHelper {
        private FileSystemManager fileSystemManager;
        private String pathName;
        private Document fileDescriptor;
        private String message;
        private Protocol response;

        public FileDeleteHelper(FileSystemManager fileSystemManager, String pathName, Document fileDescriptor) {
            this.fileSystemManager = fileSystemManager;
            this.pathName = pathName;
            this.fileDescriptor = fileDescriptor;
        }

        public Boolean checkName() {
            if (!fileSystemManager.isSafePathName(pathName)) {
                message = "unsafe pathname given";
                response = new FileDeleteResponse(fileDescriptor, pathName, message);
                return false;
            }
            else if (!fileSystemManager.fileNameExists(pathName)) {
                message = "pathname does not exist";
                response = new FileDeleteResponse(fileDescriptor, pathName, message);
                return false;
            }
            else return true;
        }

        public void doOperation() {
            String md5 = fileDescriptor.getString("md5");
            long lastModified = fileDescriptor.getLong("lastModified");

            if (fileSystemManager.deleteFile(pathName, lastModified, md5)) {
                response = new FileDeleteResponse(fileDescriptor, pathName);
            }
            else {
                message = "there was a problem deleting the file";
                response = new FileDeleteResponse(fileDescriptor, pathName, message);
            }
        }

        public Protocol getResponse() { return response; }
    }

    private class FileModifyHelper {
        private FileSystemManager fileSystemManager;
        private String pathName;
        private Document fileDescriptor;
        private String message;
        private Protocol response;
        private Boolean fileBytesReqPending = false;
        private Protocol fileBytesRequest;

        public FileModifyHelper(FileSystemManager fileSystemManager, String pathName, Document fileDescriptor) {
            this.fileSystemManager = fileSystemManager;
            this.pathName = pathName;
            this.fileDescriptor = fileDescriptor;
        }

        public Boolean checkName() {
            String md5 = fileDescriptor.getString("md5");

            if (!fileSystemManager.isSafePathName(pathName)) {
                message = "unsafe pathname given";
                response = new FileModifyResponse(fileDescriptor, pathName, message);
                return false;
            }
            else if (fileSystemManager.fileNameExists(pathName, md5)) {
                // check file name and file content
                message = "file already exists with matching content";
                response = new FileModifyResponse(fileDescriptor, pathName, message);
                return false;
            }
            else if (!fileSystemManager.fileNameExists(pathName)) {
                // check file name only
                message = "pathname does not exist";
                response = new FileModifyResponse(fileDescriptor, pathName, message);
                return false;
            }
            else return true;
        }

        public void doOperation() {
            String md5 = fileDescriptor.getString("md5");
            long lastModified = fileDescriptor.getLong("lastModified");
            long fileSize = fileDescriptor.getLong("fileSize");

            long position = 0;
            long length;
            long blockSize = Math.min(Long.parseLong(Configuration.getConfigurationValue("blockSize")), (long) 8192);

            // Determine the length of data to be request
            if (fileSize >= blockSize) {
                length = blockSize;
            } else {length = fileSize;}

            try{
                if (fileSystemManager.modifyFileLoader(pathName, md5, lastModified)) {
                    try{
                        if (!fileSystemManager.checkShortcut(pathName)) {
                            // no local copy exists; need to send File Bytes Request
                            fileBytesRequest = new FileBytesRequest(fileDescriptor, pathName, position, length);
                            fileBytesReqPending = true;
                        }
                        response = new FileModifyResponse(fileDescriptor, pathName);
                    }
                    catch (NoSuchAlgorithmException | IOException e) {
                        // do not know if local copy exists, so send File Bytes Request
                        fileBytesRequest = new FileBytesRequest(fileDescriptor, pathName, position, length);
                        fileBytesReqPending = true;
                        response = new FileModifyResponse(fileDescriptor, pathName);
                    }
                }
                else {
                    // fail to modify file loader
                    message = "there was a problem modifying the file";
                    response = new FileModifyResponse(fileDescriptor, pathName, message);
                }
            }
            catch (IOException e) {
                message = "there was a problem modifying the file";
                response = new FileModifyResponse(fileDescriptor, pathName, message);
            }
        }

        public Protocol getResponse() { return response; }

        public Boolean isFileBytesReqPending() { return fileBytesReqPending; }

        public Protocol getFileBytesRequest() { return fileBytesRequest; }

    }

    // Helper for file bytes reads and writes
    private class FileBytesRequestHelper {
        private FileSystemManager fileSystemManager;
        private Protocol protocol;
        private Protocol response;

        public FileBytesRequestHelper(FileSystemManager fileSystemManager, Protocol protocol) {
            this.fileSystemManager = fileSystemManager;
            this.protocol = protocol;
        }

        public Boolean checkName() {

            String pathName = ((FileBytesRequest) protocol).pathname;
            long length = ((FileBytesRequest) protocol).length;
            long position = ((FileBytesRequest) protocol).position;
            Document fileDescriptor = ((FileBytesRequest) protocol).fileDescriptor;
            String content = "";

            if (!fileSystemManager.isSafePathName(pathName)) {
                String errorMsg = "unsafe pathname given";
                response = new FileBytesResponse(fileDescriptor, pathName, position, length, content, errorMsg);
                return false;
            }
            else return true;
        }

        public void doOperation() {
            String pathName = ((FileBytesRequest) protocol).pathname;
            long length = ((FileBytesRequest) protocol).length;
            long position = ((FileBytesRequest) protocol).position;
            Document fileDescriptor = ((FileBytesRequest) protocol).fileDescriptor;
            String md5 = fileDescriptor.getString("md5");
            String content = "";

            try {
                ByteBuffer byteBuffer = fileSystemManager.readFile(md5, position, length);
                if (byteBuffer != null) {
                    content = Base64.getEncoder().encodeToString(byteBuffer.array());
                    response = new FileBytesResponse(fileDescriptor, pathName, position, length, content);
                }
                else {
                    String errorMsg = "unsuccessful read";
                    response = new FileBytesResponse(fileDescriptor, pathName, position, length, content, errorMsg);
                }
            }
            catch (NoSuchAlgorithmException|IOException e) {
                System.out.println("Problem in IO");
                String errorMsg = "unsuccessful read";
                response = new FileBytesResponse(fileDescriptor, pathName, position, length, content, errorMsg);
            }
        }

        public Protocol getResponse() { return response; }
    }

    private class FileBytesResponseHelper {
        private FileSystemManager fileSystemManager;
        private Protocol protocol;
        private Protocol response;

        public FileBytesResponseHelper(FileSystemManager fileSystemManager, Protocol protocol) {
            this.fileSystemManager = fileSystemManager;
            this.protocol = protocol;
        }

        public Boolean checkName() {

            String pathName = ((FileBytesResponse) protocol).pathname;

            if (fileSystemManager.isSafePathName(pathName)) {
                return true;
            } else return false;

        }

        public Boolean doOperation() {
            String pathName = ((FileBytesResponse) protocol).pathname;
            long length = ((FileBytesResponse) protocol).length;
            long position = ((FileBytesResponse) protocol).position;
            Document fileDescriptor = ((FileBytesResponse) protocol).fileDescriptor;

            if (((FileBytesResponse) protocol).status) {
                String content = ((FileBytesResponse) protocol).content;
                byte[] decodedContent = Base64.getDecoder().decode(content);
                ByteBuffer src = ByteBuffer.wrap(decodedContent);

                try {
                    if (fileSystemManager.writeFile(pathName, src, position)) {

                        long nextPosition = position + length;
                        long nextLength;
                        long fileSize = fileDescriptor.getLong("fileSize");
                        long remainingFileSize = fileSize - (position + length);

                        // Check if the complete file has been written; if not, send the next File Bytes Request
                        if (remainingFileSize > 0) {
                            long blockSize = Math.min(Long.parseLong(Configuration.getConfigurationValue("blockSize")), (long) 8192);
                            if (remainingFileSize >= blockSize) {
                                nextLength = blockSize;
                            } else {
                                nextLength = remainingFileSize;
                            }
                            response = new FileBytesRequest(fileDescriptor, pathName, nextPosition, nextLength);
                            return true;
                        } else {
                            try {
                                // call checkWriteComplete to double check and properly close the file loader
                                if (!fileSystemManager.checkWriteComplete(pathName)) {
                                    fileSystemManager.cancelFileLoader(pathName);
                                }
                                return false;
                            } catch (NoSuchAlgorithmException | IOException e) {
                                System.out.println("Problem in IO");
                                fileSystemManager.cancelFileLoader(pathName);
                                return false;
                            }
                        }
                    } else {
                        try {
                            fileSystemManager.cancelFileLoader(pathName);
                        } catch (IOException e) {
                            System.out.println("Problem in IO");
                        }
                        return false;
                    }
                } catch (IOException e) {
                    System.out.println("Problem in IO");
                    try {
                        fileSystemManager.cancelFileLoader(pathName);
                    } catch (IOException exception) {
                        System.out.println("Problem in IO");
                    }
                    return false;
                }
            } else {// the peer failed to read file bytes so the user removes the file loader
                try {
                    fileSystemManager.cancelFileLoader(pathName);
                } catch (IOException exception) {
                    System.out.println("Problem in IO");
                }
                return false;
            }
        }

        public Protocol getResponse() { return response; }
    }
}
