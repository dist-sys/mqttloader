package mqttloader.client;

import java.util.ArrayList;

import mqttloader.record.Latency;
import mqttloader.record.Throughput;

public interface IClient {
    String getClientId();
    void start();
    void disconnect();
    ArrayList<Throughput> getThroughputs();
    ArrayList<Latency> getLatencies();
}
