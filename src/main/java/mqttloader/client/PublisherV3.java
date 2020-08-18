package mqttloader.client;

import java.util.TreeMap;

import mqttloader.Loader;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;


public class PublisherV3 implements Runnable, IPublisher {
    private static String CLIENT_ID_PREFIX = "mqttloaderclient-pub";

    private MqttClient client;
    private final String clientId;
    private MqttMessage message = new MqttMessage();
    private boolean hasInterval = Loader.pubInterval>0;

    private TreeMap<Integer, Integer> throughputs = new TreeMap<>();

    public PublisherV3(int clientNumber) {
        message.setQos(Loader.pubQos);
        message.setRetained(Loader.retain);

        clientId = CLIENT_ID_PREFIX + String.format("%06d", clientNumber);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(4);
        try {
            client = new MqttClient(Loader.broker, clientId);
            client.connect(options);
            Loader.logger.info("Publisher client is connected: "+clientId);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        for(int i=0;i<Loader.numMessage;i++){
            if(!client.isConnected()) break;
            message.setPayload(Loader.genPayloads(Loader.payloadSize));
            try{
                client.publish(Loader.topic, message);
            } catch(MqttException me) {
                me.printStackTrace();
            }

            int slot = (int)((Loader.getTime()-Loader.startTime)/1000);
            int count = throughputs.containsKey(slot) ? throughputs.get(slot)+1 : 1;
            throughputs.put(slot, count);

            Loader.logger.fine("Published a message (" + Loader.topic + "): "+clientId);

            if(hasInterval){
                try {
                    Thread.sleep(Loader.pubInterval);
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
