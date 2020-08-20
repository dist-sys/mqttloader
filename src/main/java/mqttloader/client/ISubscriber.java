package mqttloader.client;

import java.util.ArrayList;
import java.util.TreeMap;

public interface ISubscriber {
    String CLIENT_ID_PREFIX = "mqttloaderclient-sub";

    String getClientId();
    TreeMap<Integer, Integer> getThroughputs();
    ArrayList<Integer> getLatencies();
    void disconnect();
}
