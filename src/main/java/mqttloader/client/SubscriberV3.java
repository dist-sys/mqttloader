package mqttloader.client;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.TreeMap;

import mqttloader.Loader;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;


public class SubscriberV3 implements MqttCallback, ISubscriber {
    private MqttClient client;
    private final String clientId;

    private TreeMap<Integer, Integer> throughputs = new TreeMap<>();
    private ArrayList<Integer> latencies = new ArrayList<>();

    public SubscriberV3(int clientNumber, String broker, int qos, String topic) {
        clientId = CLIENT_ID_PREFIX + String.format("%06d", clientNumber);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(4);
        try {
            client = new MqttClient(broker, clientId);
            client.setCallback(this);
            client.connect(options);
            Loader.logger.info("Subscriber client is connected: "+clientId);
            client.subscribe(topic, qos);
            Loader.logger.info("Subscribed (" + topic + ", QoS:" + qos + "): " + clientId);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void disconnect() {
        try {
            client.disconnect();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public TreeMap<Integer, Integer> getThroughputs() {
        return throughputs;
    }

    public ArrayList<Integer> getLatencies() {
        return latencies;
    }

    @Override
    public void connectionLost(Throwable cause) {}

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        long time = Loader.getTime();
        int slot = (int)((time-Loader.startTime)/1000);
        int count = throughputs.containsKey(slot) ? throughputs.get(slot)+1 : 1;
        throughputs.put(slot, count);

        long pubTime = ByteBuffer.wrap(message.getPayload()).getLong();
        latencies.add((int)(time-pubTime));

        Loader.lastRecvTime = time;

        Loader.logger.fine("Received a message (" + topic + "): "+clientId);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {}
}
