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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import mqttloader.Loader;
import mqttloader.Util;
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

        clientId = PUB_CLIENT_ID_PREFIX + String.format("%05d", clientNumber);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(4);
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
        if(pubInterval==0){
            continuousRun();
        }else{
            periodicalRun();
        }
    }

    public void continuousRun() {
        for(int i=0;i<numMessage;i++){
            if (cancelled) {
                Loader.logger.info("Publish task is cancelled: "+clientId);
                break;
            }
            if (client.isConnected()) {
                publish();
            } else {
                Loader.logger.warning("On sending publish, client was not connected: "+clientId);
            }
        }

        Loader.logger.info("Publisher finishes to send publish: "+clientId);
        Loader.countDownLatch.countDown();
    }

    public void periodicalRun() {
        if(numMessage > 0) {
            if (client.isConnected()) {
                publish();
            } else {
                Loader.logger.warning("On sending publish, client was not connected: "+clientId);
            }

            numMessage--;
            if(numMessage==0){
                Loader.logger.info("Publisher finishes to send publish: "+clientId);
                Loader.countDownLatch.countDown();
            }
        }
    }

    public void publish() {
        long currentTime = Util.getCurrentTimeMillis();
        message.setPayload(Util.genPayloads(payloadSize, currentTime));
        try {
            client.publish(topic, message);
        } catch (MqttException me) {
            Loader.logger.warning("On sending publish, MqttException occurred: "+clientId);
            me.printStackTrace();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(currentTime);
        sb.append(",");
        sb.append(clientId);
        sb.append(",S,");
        Loader.queue.offer(new String(sb));

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

        if (client.isConnected()) {
            try {
                client.disconnect();
                Loader.logger.info("Publisher client is disconnected: "+clientId);
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String getClientId() {
        return clientId;
    }
}
