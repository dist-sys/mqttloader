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

package mqttloader;

import java.time.Instant;

public class Record {
    private long sentEpochMicros;
    private Instant receivedTime;
    private String clientId;
    private boolean isSend;

    private boolean isStopSignal = false;

    public Record(long sentEpochMicros, Instant receivedTime, String clientId, boolean isSend) {
        this.sentEpochMicros = sentEpochMicros;
        this.receivedTime = receivedTime;
        this.clientId = clientId;
        this.isSend = isSend;
    }

    public Record(long sentEpochMicros, String clientId, boolean isSend) {
        this(sentEpochMicros, null, clientId, isSend);
    }

    public Record() {
        this.isStopSignal = true;
    }

    public long getSentEpochMicros() {
        return sentEpochMicros;
    }

    public Instant getReceivedTime() {
        return receivedTime;
    }

    public String getClientId() {
        return clientId;
    }

    public boolean isSend() {
        return isSend;
    }

    public boolean isStopSignal() {
        return isStopSignal;
    }

    public long getLatency() {
        long latency = Util.getEpochMicros(receivedTime) - sentEpochMicros;
        if(latency < 0) {
            // If running MQTTLoader on multiple machines, a slight time error may cause a negative value of latency.
            Loader.LOGGER.fine("Negative value of latency is converted to zero.");
            return 0;
        } else {
            return latency;
        }
    }
}
