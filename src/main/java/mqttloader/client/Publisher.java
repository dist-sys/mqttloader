package mqttloader.client;

import java.util.TreeMap;

import mqttloader.Loader;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;

public class Publisher implements Runnable, IPublisher {
    private MqttClient client;
    private final String clientId;
    private String topic;
    private int payloadSize;
    private int numMessage;
    private int pubInterval;
    private MqttMessage message = new MqttMessage();
    private boolean hasInterval;

    private TreeMap<Integer, Integer> throughputs = new TreeMap<>();

    public Publisher(int clientNumber, String broker, int qos, boolean retain, String topic, int payloadSize, int numMessage, int pubInterval) {
        message.setQos(qos);
        message.setRetained(retain);
        this.topic = topic;
        this.payloadSize = payloadSize;
        this.numMessage = numMessage;
        this.pubInterval = pubInterval;
        hasInterval = pubInterval > 0;

        clientId = CLIENT_ID_PREFIX + String.format("%06d", clientNumber);
        MqttConnectionOptions options = new MqttConnectionOptions();
        try {
            client = new MqttClient(broker, clientId);
            client.connect(options);
            Loader.logger.info("Publisher client is connected: "+clientId);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        for(int i=0;i<numMessage;i++){
            if(!client.isConnected()) break;
            message.setPayload(Loader.genPayloads(payloadSize));
            try{
                client.publish(topic, message);
            } catch(MqttException me) {
                me.printStackTrace();
            }

            int slot = (int)((Loader.getTime()-Loader.startTime)/1000);
            int count = throughputs.containsKey(slot) ? throughputs.get(slot)+1 : 1;
            throughputs.put(slot, count);

            Loader.logger.fine("Published a message (" + topic + "): "+clientId);

            if(hasInterval){
                try {
                    Thread.sleep(pubInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        Loader.countDownLatch.countDown();
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
}
