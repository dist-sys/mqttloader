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
import mqttloader.Record;
import mqttloader.Recorder;

public abstract class AbstractPublisher extends AbstractClient implements Runnable {
    protected final String topic;
    protected final int payloadSize;
    protected int numMessage;
    protected final int pubInterval;

    protected ScheduledExecutorService service;
    protected ScheduledFuture future;
    protected volatile boolean cancelled = false;
    private Recorder recorder;

    public AbstractPublisher(int clientNumber, String topic, int payloadSize, int numMessage, int pubInterval, Recorder recorder) {
        super(PUB_CLIENT_ID_PREFIX + String.format("%05d", clientNumber));
        this.topic = topic;
        this.payloadSize = payloadSize;
        this.numMessage = numMessage;
        this.pubInterval = pubInterval;
        this.recorder = recorder;
    }

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

    private void continuousRun() {
        for(int i=0;i<numMessage;i++){
            if(cancelled) {
                Loader.LOGGER.info("Publish task cancelled (" + clientId + ").");
                break;
            }
            if(isConnected()) {
                publish();
            } else {
                Loader.LOGGER.warning("Failed to publish (" + clientId + ").");
            }
        }

        Loader.LOGGER.info("Completed to publish (" + clientId + ").");
        Loader.cdl.countDown();
    }

    private void periodicalRun() {
        if(numMessage > 0) {
            if(isConnected()) {
                publish();
            } else {
                Loader.LOGGER.warning("Failed to publish (" + clientId + ").");
            }

            numMessage--;
            if(numMessage==0){
                Loader.LOGGER.info("Completed to publish (" + clientId + ").");
                Loader.cdl.countDown();
            }
        }
    }

    protected void recordSend(long currentTime) {
        recorder.record(new Record(currentTime, clientId, true));
//        Loader.LOGGER.fine("Published a message to topic \"" + topic + "\" (" + clientId + ").");
    }

    protected void terminateTasks() {
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
    }

    protected abstract void publish();
    protected abstract boolean isConnected();
}
