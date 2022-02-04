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
import java.time.Instant;
import mqttloader.Loader;
import mqttloader.Record;
import mqttloader.Recorder;
import mqttloader.Util;

public abstract class AbstractSubscriber extends AbstractClient {
    private Recorder recorder;

    public AbstractSubscriber(int clientNumber, Recorder recorder) {
        super(SUB_CLIENT_ID_PREFIX + String.format("%05d", clientNumber));
        this.recorder = recorder;
    }

    protected void recordReceive(String topic, byte[] payload) {
        // Skip if preparation has not been completed yet.
        if(Loader.measurementStartTime==null) {
            return;
        }

        Instant currentTime = Util.getCurrentTimeWithOffset();
        long currentTimeMillis = Util.getEpochMillis(currentTime);
        long pubTime = ByteBuffer.wrap(payload).getLong();

        int latency = (int)(currentTimeMillis - pubTime);
        if (latency < 0) {
            // If running MQTTLoader on multiple machines, a slight time error may cause a negative value of latency.
            latency = 0;
            Loader.LOGGER.fine("Negative value of latency is converted to zero.");
        }

        recorder.record(new Record(currentTimeMillis, clientId, false, latency));
        Loader.lastRecvTime = currentTime;
//        Loader.LOGGER.fine("Received a message on topic \"" + topic + "\" (" + clientId + ").");
    }
}
