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

import mqttloader.Loader;
import mqttloader.Recorder;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;


public class SubscriberV3 extends AbstractSubscriber implements MqttCallback {
    private MqttClient client;

    public SubscriberV3(int clientNumber, String broker, int qos, String topic, Recorder recorder) {
        super(clientNumber, recorder);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(4);
        options.setCleanSession(true);
        try {
            client = new MqttClient(broker, clientId, new MemoryPersistence());
            client.setCallback(this);
            client.connect(options);
            Loader.LOGGER.info("Subscriber " + clientId + " connected.");
            client.subscribe(topic, qos);
            Loader.LOGGER.info("Subscribed to topic \"" + topic + "\" with QoS " + qos + " (" + clientId + ").");
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
    public void connectionLost(Throwable cause) {}

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        recordReceive(topic, message.getPayload());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {}
}
