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

import static mqttloader.Constants.SUB_CLIENT_ID_PREFIX;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import mqttloader.Loader;
import mqttloader.Util;
import mqttloader.record.Latency;
import mqttloader.record.Throughput;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

public class Subscriber implements MqttCallback, IClient {
    private MqttClient client;
    private final String clientId;

    private ArrayList<Throughput> throughputs = new ArrayList<>();
    private ArrayList<Latency> latencies = new ArrayList<>();

    public Subscriber(int clientNumber, String broker, int qos, boolean shSub, String topic) {
        clientId = SUB_CLIENT_ID_PREFIX + String.format("%06d", clientNumber);
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
    public void start(){
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
    public ArrayList<Throughput> getThroughputs() {
        return throughputs;
    }

    @Override
    public ArrayList<Latency> getLatencies() {
        return latencies;
    }

    @Override
    public void disconnected(MqttDisconnectResponse disconnectResponse) {}

    @Override
    public void mqttErrorOccurred(MqttException exception) {}

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        long time = Util.getTime();
        int slot = (int)((time-Loader.startTime)/1000);
        if(throughputs.size()>0){
            Throughput lastTh = throughputs.get(throughputs.size()-1);
            if(lastTh.getSlot() == slot) {
                lastTh.setCount(lastTh.getCount()+1);
            }else{
                throughputs.add(new Throughput(slot, 1));
            }
        }else{
            throughputs.add(new Throughput(slot, 1));
        }

        long pubTime = ByteBuffer.wrap(message.getPayload()).getLong();
        latencies.add(new Latency(slot, (int)(time-pubTime)));

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
