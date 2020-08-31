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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import mqttloader.Loader;
import mqttloader.Util;
import mqttloader.record.Latency;
import mqttloader.record.Throughput;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class PublisherV3 implements Runnable, IClient {
    private MqttClient client;
    private final String clientId;
    private final String topic;
    private final int payloadSize;
    private int numMessage;
    private final int pubInterval;
    private MqttMessage message = new MqttMessage();

    // If change publisher to be multi-threaded, throughputs (and others) should be thread-safe.
    private ArrayList<Throughput> throughputs = new ArrayList<>();

    private ScheduledExecutorService service;
    private ScheduledFuture future;

    private volatile boolean cancelled = false;

    public PublisherV3(int clientNumber, String broker, int qos, boolean retain, String topic, int payloadSize, int numMessage, int pubInterval) {
        message.setQos(qos);
        message.setRetained(retain);
        this.topic = topic;
        this.payloadSize = payloadSize;
        this.numMessage = numMessage;
        this.pubInterval = pubInterval;

        clientId = PUB_CLIENT_ID_PREFIX + String.format("%06d", clientNumber);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(4);
        try {
            client = new MqttClient(broker, clientId);
            client.connect(options);
            Loader.logger.info("Publisher client is connected: "+clientId);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void start(long delay) {
        service = Executors.newSingleThreadScheduledExecutor();
        if(pubInterval==0){
            future = service.schedule(this, delay, TimeUnit.MILLISECONDS);
        }else{
            future = service.scheduleAtFixedRate(this, delay, pubInterval, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void run() {
        if(!client.isConnected()) {
            Loader.countDownLatch.countDown();
        } else {
            if(pubInterval==0){
                continuousRun();
            }else{
                periodicalRun();
            }
        }
    }

    public void continuousRun() {
        for(int i=0;i<numMessage;i++){
            if(cancelled) {
                break;
            }
            publish();
        }

        Loader.countDownLatch.countDown();
    }

    public void periodicalRun() {
        if(numMessage > 0) {
            publish();

            numMessage--;
            if(numMessage==0){
                Loader.countDownLatch.countDown();
            }
        }
    }

    public void publish() {
        message.setPayload(Util.genPayloads(payloadSize));
        try {
            client.publish(topic, message);
        } catch (MqttException e) {
            e.printStackTrace();
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

    @Override
    public void disconnect() {
        if(!future.isDone()) {
            cancelled = true;
            future.cancel(false);
        }

        service.shutdown();
        try {
            service.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
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
