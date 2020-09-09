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

import mqttloader.Loader;
import mqttloader.Record;
import mqttloader.Util;

public abstract class AbstractSubscriber extends AbstractClient {
    public AbstractSubscriber(int clientNumber) {
        super(SUB_CLIENT_ID_PREFIX + String.format("%05d", clientNumber));
    }

    protected void recordReceive(String topic, byte[] payload) {
        long currentTime = Util.getCurrentTimeMillis();
        long pubTime = ByteBuffer.wrap(payload).getLong();

        int latency = (int)(currentTime - pubTime);
        if (latency < 0) {
            // If running MQTTLoader on multiple machines, a slight time error may cause a negative value of latency.
            latency = 0;
            Loader.logger.fine("Negative value of latency is converted to zero.");
        }

        Loader.queue.offer(new Record(currentTime, clientId, false, latency));
        Loader.lastRecvTime = currentTime;
        Loader.logger.fine("Received a message on topic \"" + topic + "\" (" + clientId + ").");
    }
}
