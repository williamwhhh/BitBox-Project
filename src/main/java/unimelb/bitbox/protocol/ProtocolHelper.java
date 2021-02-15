package unimelb.bitbox.protocol;

import unimelb.bitbox.util.Document;

/**
 * Helps to manipulate protocols.
 */
public class ProtocolHelper {

    /**
     * Convert a string to corresponding protocol. The integrity of protocol also checked.
     * @param str The string need to be converted.
     * @return The corresponding Protocol object, null if the string is invalid.
     */
    public static Protocol convertProtocol(String str) {
        // the string is null
        if(str == null){
            return null;
        }
        Document doc = Document.parse(str);
        String command = doc.getString("command");
        // the string can not convert to Document object
        if(command == null){
            return null;
        }
        // check integrity for each protocol
        switch(doc.get("command").toString()) {

            case "CONNECTION_REFUSED":
                return ConnectionRefused.convert(doc);

            case "FILE_MODIFY_REQUEST":
                return FileModifyRequest.convert(doc);

            case "FILE_MODIFY_RESPONSE":
                return FileModifyResponse.convert(doc);

            case "HANDSHAKE_REQUEST":
                return HandshakeRequest.convert(doc);

            case "HANDSHAKE_RESPONSE":
                return HandshakeResponse.convert(doc);

            case "INVALID_PROTOCOL":
                return InvalidProtocol.convert(doc);

            case "DIRECTORY_CREATE_REQUEST":
                return DirectoryCreateRequest.convert(doc);

            case "DIRECTORY_CREATE_RESPONSE":
                return DirectoryCreateResponse.convert(doc);

            case "DIRECTORY_DELETE_REQUEST":
                return DirectoryDeleteRequest.convert(doc);

            case "DIRECTORY_DELETE_RESPONSE":
                return DirectoryDeleteResponse.convert(doc);

            case "FILE_BYTES_REQUEST":
                return FileBytesRequest.convert(doc);

            case "FILE_BYTES_RESPONSE":
                return FileBytesResponse.convert(doc);

            case "FILE_CREATE_REQUEST":
                return FileCreateRequest.convert(doc);

            case "FILE_CREATE_RESPONSE":
                return FileCreateResponse.convert(doc);

            case "FILE_DELETE_REQUEST":
                return FileDeleteRequest.convert(doc);

            case "FILE_DELETE_RESPONSE":
                return FileDeleteResponse.convert(doc);

            case "AUTH_REQUEST":
                return ClientConnectionRequest.convert(doc);

            case "AUTH_RESPONSE":
                return ClientConnectionResponse.convert(doc);

            case "CONNECT_PEER_REQUEST":
                return ConnectPeerRequest.convert(doc);

            case "CONNECT_PEER_RESPONSE":
                return ConnectPeerResponse.convert(doc);

            case "DISCONNECT_PEER_REQUEST":
                return DisconnectPeerRequest.convert(doc);

            case "DISCONNECT_PEER_RESPONSE":
                return DisconnectPeerResponse.convert(doc);

            case "LIST_PEERS_REQUEST":
                return ListPeersRequest.convert(doc);

            case "LIST_PEERS_RESPONSE":
                return ListPeersResponse.convert(doc);

            default:
                return null;
        }

    }

}
