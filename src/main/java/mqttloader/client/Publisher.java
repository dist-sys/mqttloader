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

import static mqttloader.Constants.PUB_CLIENT_ID_PREFIX;

import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import mqttloader.Loader;
import mqttloader.Util;
import mqttloader.record.Latency;
import mqttloader.record.Throughput;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;

public class Publisher implements Runnable, IClient {
    private MqttClient client;
    private final String clientId;
    private String topic;
    private int payloadSize;
    private int numMessage;
    private int pubInterval;
    private MqttMessage message = new MqttMessage();
    private boolean hasInterval;

    private ArrayList<Throughput> throughputs = new ArrayList<>();

    private ScheduledThreadPoolExecutor service;
    private ScheduledFuture future;

    public Publisher(int clientNumber, String broker, int qos, boolean retain, String topic, int payloadSize, int numMessage, int pubInterval) {
        message.setQos(qos);
        message.setRetained(retain);
        this.topic = topic;
        this.payloadSize = payloadSize;
        this.numMessage = numMessage;
        this.pubInterval = pubInterval;
        hasInterval = pubInterval > 0;

        clientId = PUB_CLIENT_ID_PREFIX + String.format("%06d", clientNumber);
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
    public void start() {
        service = new ScheduledThreadPoolExecutor(1);
        if(pubInterval==0){
            future = service.schedule(this, 0, TimeUnit.MILLISECONDS);
        }else{
            future = service.scheduleAtFixedRate(this, 0, pubInterval, TimeUnit.MILLISECONDS);
        }
    }

    public void terminate() {
        service.shutdown();
        Loader.countDownLatch.countDown();
    }

    public void publish() {
        message.setPayload(Util.genPayloads(payloadSize));
        try{
            client.publish(topic, message);
        } catch(MqttException me) {
            me.printStackTrace();
        }

        int slot = (int)((Util.getTime()-Loader.startTime)/1000);
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

        Loader.logger.fine("Published a message (" + topic + "): "+clientId);
    }

    public void periodicalRun() {
        publish();

        numMessage--;
        if(numMessage==0){
            terminate();
        }
    }

    public void continuousRun() {
        for(int i=0;i<numMessage;i++){
            if(future.isCancelled()) break;
            publish();
        }

        terminate();
    }

    @Override
    public void run() {
        if(!client.isConnected()) {
            terminate();
        }

        if(pubInterval==0){
            continuousRun();
        }else{
            periodicalRun();
        }
    }

    @Override
    public void disconnect() {
        if(!future.isDone()) {
            future.cancel(false);
            service.shutdown();
        }

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
    public ArrayList<Latency> getLatencies(){
        return null;
    }
}
