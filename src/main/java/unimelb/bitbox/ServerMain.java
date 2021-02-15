package unimelb.bitbox;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import unimelb.bitbox.protocol.Protocol;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;


public class ServerMain implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	protected FileSystemManager fileSystemManager;
	protected CommunicationModule communicationModule;
	protected ClientManager clientManager;

	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
		fileSystemManager=new FileSystemManager(Configuration.getConfigurationValue("path"),this);

		if(Configuration.getConfigurationValue("mode").equals("tcp")){
			communicationModule = new TCPCommunicationModule(fileSystemManager);
		}
		else if(Configuration.getConfigurationValue("mode").equals("udp")){
			communicationModule = new UDPCommunicationModule(fileSystemManager);
		}
		else{
			System.exit(1);
		}

		clientManager = new ClientManager(communicationModule);
		// Connect to peers in config
		String[] initpeers = Configuration.getConfigurationValue("peers").split(",");
		for(String p: initpeers){
			communicationModule.initHandshake(ConnectedPeer.convertConnectedPeer(p));
		}
	}

	@Override
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
		// TODO: process events
		// Convert file system event to protocol
		Protocol request = EventHandler.handleEvent(fileSystemEvent);
		if (request!= null) {
			// Sent request to all connected peers
			communicationModule.broadcast(request);
		}
	}
}
