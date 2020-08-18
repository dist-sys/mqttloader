package mqttloader.client;

import java.util.ArrayList;
import java.util.TreeMap;

public interface ISubscriber {
    public String getClientId();
    public TreeMap<Integer, Integer> getThroughputs();
    public ArrayList<Integer> getLatencies();
    public void disconnect();
}
