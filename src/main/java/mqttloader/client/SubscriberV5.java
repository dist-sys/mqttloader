/*
 * Copyright 2020 Distributed Systems Group
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mqttloader.client;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import mqttloader.Loader;
import mqttloader.Recorder;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

public class SubscriberV5 extends AbstractSubscriber implements MqttCallback {
    private MqttClient client;

    public SubscriberV5(int clientNumber, String broker, String userName, String password, String trustStore, String keyStore, int qos, boolean shSub, String topic, Recorder recorder) {
        super(clientNumber, recorder);
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        if(userName != null) options.setUserName(userName);
        if(password != null) options.setPassword(password.getBytes(StandardCharsets.UTF_8));
        if(trustStore != null) {
            Properties prop = new Properties();
            prop.setProperty("com.ibm.ssl.trustStore", trustStore);
            if(keyStore != null) {
                prop.setProperty("com.ibm.ssl.keyStore", keyStore);
            }
            options.setSSLProperties(prop);
        }
        try {
            client = new MqttClient(broker, clientId, new MemoryPersistence());
            client.setCallback(this);
            client.connect(options);
            Loader.LOGGER.info("Subscriber " + clientId + " connected.");
            String t;
            if(shSub){
                t = "$share/mqttload/"+topic;
            }else{
                t = topic;
            }
            client.subscribe(t, qos);
            Loader.LOGGER.info("Subscribed to topic \"" + t + "\" with QoS " + qos + " (" + clientId + ").");
        } catch (MqttException e) {
            Loader.LOGGER.warning("Subscriber failed to connect (" + clientId + ").");
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public void disconnect() {
        if (client.isConnected()) {
            try {
                client.disconnect();
                Loader.LOGGER.info("Subscriber " + clientId + " disconnected.");
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void disconnected(MqttDisconnectResponse disconnectResponse) {}

    @Override
    public void mqttErrorOccurred(MqttException exception) {}

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        recordReceive(topic, message.getPayload());
    }

    @Override
    public void deliveryComplete(IMqttToken token) {}

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {}

    @Override
    public void authPacketArrived(int reasonCode, MqttProperties properties) {}
}
