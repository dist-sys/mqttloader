package mqttloader.client;

import java.util.TreeMap;

public interface IPublisher {
    public String getClientId();
    public TreeMap<Integer, Integer> getThroughputs();
    public void disconnect();
}
