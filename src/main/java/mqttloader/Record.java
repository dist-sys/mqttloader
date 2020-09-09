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

public class Record {
    private long timestamp;
    private String clientId;
    private boolean isSend;
    private int latency;

    private boolean isStopSignal = false;

    public Record(long timestamp, String clientId, boolean isSend, int latency) {
        this.timestamp = timestamp;
        this.clientId = clientId;
        this.isSend = isSend;
        this.latency = latency;
    }

    public Record(long timestamp, String clientId, boolean isSend) {
        this(timestamp, clientId, isSend, -1);
    }

    public Record() {
        this.isStopSignal = true;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getClientId() {
        return clientId;
    }

    public boolean isSend() {
        return isSend;
    }

    public int getLatency() {
        return latency;
    }

    public boolean isStopSignal() {
        return isStopSignal;
    }
}
