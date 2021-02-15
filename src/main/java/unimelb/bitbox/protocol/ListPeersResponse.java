package unimelb.bitbox.protocol;

import unimelb.bitbox.ConnectedPeer;
import unimelb.bitbox.util.Document;

import java.util.ArrayList;
import java.util.Date;

/**
 * Class for ListPeersResponse protocol
 */
public class ListPeersResponse implements Protocol{

    public ArrayList<ConnectedPeer> peers;
    private int retryNum = 0;
    private long createTime = (new Date()).getTime();

    public ListPeersResponse(ArrayList<ConnectedPeer> peers) {
        this.peers = new ArrayList<>(peers);

    }

    @Override
    public String toString() {
        Document doc = new Document();
        doc.append("command", "LIST_PEERS_RESPONSE");
        ArrayList<Document> peerDocs = new ArrayList<>();
        for (ConnectedPeer peer:peers){
            peerDocs.add(peer.toDoc());
        }
        doc.append("peers",peerDocs);
        return doc.toJson();
    }

    public static ListPeersResponse convert(Document doc){
        if (doc.containsKey("peers")){
            ArrayList<ConnectedPeer> peers = new ArrayList<>();
            for(Document peer: (ArrayList<Document>) doc.get("peers")){
                peers.add(new ConnectedPeer(peer.getString("host"), (int) peer.getLong("port")));
            }
            return new ListPeersResponse(peers);
        } else{
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
        return false;
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
