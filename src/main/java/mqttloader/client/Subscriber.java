package mqttloader.client;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.TreeMap;

import mqttloader.Loader;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

public class Subscriber implements MqttCallback, ISubscriber {
    private MqttClient client;
    private final String clientId;

    private TreeMap<Integer, Integer> throughputs = new TreeMap<>();
    private ArrayList<Integer> latencies = new ArrayList<>();

    public Subscriber(int clientNumber, String broker, int qos, boolean shSub, String topic) {
        clientId = CLIENT_ID_PREFIX + String.format("%06d", clientNumber);
        MqttConnectionOptions options = new MqttConnectionOptions();
        try {
            client = new MqttClient(broker, clientId);
            client.setCallback(this);
            client.connect(options);
            Loader.logger.info("Subscriber client is connected: "+clientId);
            String t;
            if(shSub){
                t = "$share/mqttload/"+topic;
            }else{
                t = topic;
            }
            client.subscribe(t, qos);
            Loader.logger.info("Subscribed (" + t + ", QoS:" + qos + "): " + clientId);
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

    @Override
    public ArrayList<Integer> getLatencies() {
        return latencies;
    }

    @Override
    public void disconnected(MqttDisconnectResponse disconnectResponse) {}

    @Override
    public void mqttErrorOccurred(MqttException exception) {}

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
    public void deliveryComplete(IMqttToken token) {}

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {}

    @Override
    public void authPacketArrived(int reasonCode, MqttProperties properties) {}
}
