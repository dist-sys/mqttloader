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
import mqttloader.Util;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;

public class Publisher extends AbstractPublisher {
    private MqttClient client;
    private MqttMessage message = new MqttMessage();

    public Publisher(int clientNumber, String broker, int qos, boolean retain, String topic, int payloadSize, int numMessage, int pubInterval) {
        super(clientNumber, topic, payloadSize, numMessage, pubInterval);
        message.setQos(qos);
        message.setRetained(retain);

        MqttConnectionOptions options = new MqttConnectionOptions();
        try {
            client = new MqttClient(broker, clientId);
            client.connect(options);
            Loader.logger.info("Publisher client is connected: "+clientId);
        } catch (MqttException e) {
            Loader.logger.warning("Publisher client fails to connect: "+clientId);
            e.printStackTrace();
        }
    }

    @Override
    protected void publish() {
        long currentTime = Util.getCurrentTimeMillis();
        message.setPayload(Util.genPayloads(payloadSize, currentTime));
        try {
            client.publish(topic, message);
        } catch (MqttException me) {
            Loader.logger.warning("On sending publish, MqttException occurred: "+clientId);
            me.printStackTrace();
        }

        recordSend(currentTime);
    }

    @Override
    protected boolean isConnected() {
        return client.isConnected();
    }

    @Override
    public void disconnect() {
        terminateTasks();

        if (client.isConnected()) {
            try {
                client.disconnect();
                Loader.logger.info("Publisher client is disconnected: "+clientId);
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }
}
