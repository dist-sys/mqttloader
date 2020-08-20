package mqttloader.client;

import java.util.TreeMap;

public interface IPublisher {
    String CLIENT_ID_PREFIX = "mqttloaderclient-pub";

    String getClientId();
    TreeMap<Integer, Integer> getThroughputs();
    void disconnect();
}
