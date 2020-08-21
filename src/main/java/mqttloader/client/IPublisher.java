package mqttloader.client;

import java.util.ArrayList;

import mqttloader.record.Throughput;

public interface IPublisher {
    String CLIENT_ID_PREFIX = "mqttloaderclient-pub";

    void start();
    String getClientId();
    ArrayList<Throughput> getThroughputs();
    void disconnect();
}
