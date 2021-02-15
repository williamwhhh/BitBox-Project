package unimelb.bitbox;

import unimelb.bitbox.protocol.*;
import unimelb.bitbox.util.FileSystemManager;

/**
 * Class that helps to convert file system event into corresponding protocol.
 */
public class EventHandler {

    public static Protocol handleEvent(FileSystemManager.FileSystemEvent event){
        switch (event.event){
            case DIRECTORY_CREATE:
                return new DirectoryCreateRequest(event.pathName);
            case DIRECTORY_DELETE:
                return new DirectoryDeleteRequest(event.pathName);
            case FILE_CREATE:
                return new FileCreateRequest(event.fileDescriptor.toDoc(), event.pathName);
            case FILE_DELETE:
                return new FileDeleteRequest(event.fileDescriptor.toDoc(), event.pathName);
            case FILE_MODIFY:
                return new FileModifyRequest(event.fileDescriptor.toDoc(), event.pathName);
            default:
                return null;
        }
    }
}
