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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;

public class Recorder implements Runnable {
    private final boolean inMemory;
    private final ArrayBlockingQueue<Record> queue = new ArrayBlockingQueue<>(1000000);

    private Thread thread;

    private File file;
    private FileOutputStream fos = null;
    private OutputStreamWriter osw = null;
    private BufferedWriter bw = null;

    private TreeMap<Integer, Integer> sendThroughputs = new TreeMap<>();
    private TreeMap<Integer, Integer> recvThroughputs = new TreeMap<>();
    private TreeMap<Integer, Long> latencySums = new TreeMap<>();
    private TreeMap<Integer, Integer> latencyMaxs = new TreeMap<>();

    public Recorder(File file, boolean inMemory) {
        this.inMemory = inMemory;
        this.file = file;
        if(!inMemory) {
            try {
                fos = new FileOutputStream(file, true);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            osw = new OutputStreamWriter(fos);
            bw = new BufferedWriter(osw);
        }
    }

    @Override
    public void run() {
        thread = Thread.currentThread();

        Record record = null;
        while (true) {
            try {
                record = queue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(record != null) {
                if(record.isStopSignal()) {
                    break;
                }

                if(inMemory) {
                    recordInMemory(record);
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append(record.getTimestamp());
                    sb.append(",");
                    sb.append(record.getClientId());
                    if(record.isSend()) {
                        sb.append(",S,");
                    } else {
                        sb.append(",R,");
                        sb.append(record.getLatency());
                    }

                    try {
                        bw.write(new String(sb));
                        bw.newLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if(!inMemory) {
            try{
                bw.flush();
                bw.close();
                osw.close();
                fos.close();
            } catch(IOException e){
                e.printStackTrace();
            } finally {
                try {
                    if(bw != null) bw.close();
                    if(osw != null) osw.close();
                    if(fos != null) fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }
    }

    public void recordInMemory(Record record) {
        int elapsedSecond = (int)((record.getTimestamp()-Util.getEpochMillis(Loader.measurementStartTime))/1000);
        if(record.isSend()) {
            if(sendThroughputs.containsKey(elapsedSecond)) {
                sendThroughputs.put(elapsedSecond, sendThroughputs.get(elapsedSecond)+1);
            } else {
                sendThroughputs.put(elapsedSecond, 1);
            }
        } else {
            if(recvThroughputs.containsKey(elapsedSecond)) {
                recvThroughputs.put(elapsedSecond, recvThroughputs.get(elapsedSecond)+1);
            } else {
                recvThroughputs.put(elapsedSecond, 1);
            }

            if(latencySums.containsKey(elapsedSecond)) {
                latencySums.put(elapsedSecond, latencySums.get(elapsedSecond)+(long)record.getLatency());
            } else {
                latencySums.put(elapsedSecond, (long)record.getLatency());
            }

            if(latencyMaxs.containsKey(elapsedSecond)) {
                if(latencyMaxs.get(elapsedSecond) < record.getLatency()) {
                    latencyMaxs.put(elapsedSecond, record.getLatency());
                }
            } else {
                latencyMaxs.put(elapsedSecond, record.getLatency());
            }
        }
    }

    public void record(Record record) {
        queue.offer(record);
    }

    public void start() {
        new Thread(this).start();
    }

    public void terminate() {
        queue.offer(Constants.STOP_SIGNAL);
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public File getFile() {
        return file;
    }

    public TreeMap<Integer, Integer> getSendThroughputs() {
        return sendThroughputs;
    }

    public TreeMap<Integer, Integer> getRecvThroughputs() {
        return recvThroughputs;
    }

    public TreeMap<Integer, Long> getLatencySums() {
        return latencySums;
    }

    public TreeMap<Integer, Integer> getLatencyMaxs() {
        return latencyMaxs;
    }
}
