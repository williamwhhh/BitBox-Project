package unimelb.bitbox;
import org.kohsuke.args4j.Option;

/**
 * class to deal with the command line argument
 */
public class CmdLineArgs {

    @Option(required = true, name = "-c", aliases = {"--command"}, usage = "Command")
    private String command;

    @Option(required = true, name = "-s", aliases = {"--server"}, usage = "Server address")
    private String server;

    @Option(required = false, name = "-p", aliases = {"--peer"}, usage = "Peer address")
    private String peer;

    @Option(required = true, name = "-i", aliases = {"--identity"}, usage = "Identity")
    private String identity;

    public String getServer() {
        return server;
    }

    public String getCommand() {
        return command;
    }

    public String getPeer(){
        return peer;
    }

    public String getIdentity() { return identity; }

}
