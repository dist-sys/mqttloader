package mqttloader.client;

import java.util.ArrayList;

import mqttloader.record.Latency;
import mqttloader.record.Throughput;

public interface ISubscriber {
    String CLIENT_ID_PREFIX = "mqttloaderclient-sub";

    String getClientId();
    ArrayList<Throughput> getThroughputs();
    ArrayList<Latency> getLatencies();
    void disconnect();
}
