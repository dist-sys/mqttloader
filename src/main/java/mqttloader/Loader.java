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

import static java.lang.System.exit;
import static mqttloader.Constants.Opt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import mqttloader.client.IClient;
import mqttloader.client.Publisher;
import mqttloader.client.PublisherV3;
import mqttloader.client.Subscriber;
import mqttloader.client.SubscriberV3;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

public class Loader {
    private CommandLine cmd = null;
    private ArrayList<IClient> publishers = new ArrayList<>();
    private ArrayList<IClient> subscribers = new ArrayList<>();
    public static volatile long startTime;
    public static volatile long startNanoTime;
    private long endTime;
    public static volatile long lastRecvTime;
    public static ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(1000000);
    private File file;
    public static CountDownLatch countDownLatch;
    public static Logger logger = Logger.getLogger(Loader.class.getName());
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z");

    public Loader(String[] args) {
        setOptions(args);

        String logLevel = cmd.getOptionValue(Opt.LOG_LEVEL.getName(), Opt.LOG_LEVEL.getDefaultValue());
        logger.setLevel(Level.parse(logLevel));
        logger.info("Starting mqttloader tool.");

        int numPub = Integer.valueOf(cmd.getOptionValue(Opt.NUM_PUB.getName(), Opt.NUM_PUB.getDefaultValue()));
        int numSub = Integer.valueOf(cmd.getOptionValue(Opt.NUM_SUB.getName(), Opt.NUM_SUB.getDefaultValue()));
        if (numSub > 0) {
            countDownLatch = new CountDownLatch(numPub+1);  // For waiting for publishers' completion and subscribers' timeout.
        } else {
            countDownLatch = new CountDownLatch(numPub);
        }

        logger.info("Preparing clients.");
        prepareClients();

        file = getFile();
        logger.info("Data file is placed at: "+file.getAbsolutePath());
        FileWriter writer = new FileWriter(file);
        Thread fileThread = new Thread(writer);
        fileThread.start();

        logger.info("Starting measurement.");
        startMeasurement();

        Timer timer = new Timer();
        if(numSub > 0){
            int subTimeout = Integer.valueOf(cmd.getOptionValue(Opt.SUB_TIMEOUT.getName(), Opt.SUB_TIMEOUT.getDefaultValue()));
            timer.schedule(new RecvTimeoutTask(timer, subTimeout), subTimeout*1000);
        }

        int execTime = Integer.valueOf(cmd.getOptionValue(Opt.EXEC_TIME.getName(), Opt.EXEC_TIME.getDefaultValue()));
        long holdNanoTime = Util.getElapsedNanoTime();
        if(holdNanoTime > 0) execTime += (int)(holdNanoTime/Constants.MILLISECOND_IN_NANO);
        try {
            countDownLatch.await(execTime, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if(countDownLatch.getCount()>0) {
            logger.info("Measurement timed out.");
        } else {
            logger.info("Measurement completed.");
        }

        timer.cancel();

        logger.info("Terminating clients.");
        disconnectClients();

        endTime = Util.getCurrentTimeMillis();

        queue.offer(Constants.STOP_SIGNAL);
        try {
            fileThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        logger.info("Printing results.");
        calcResult();
    }

    private void setOptions(String[] args) {
        Options options = new Options();
        for(Opt opt: Opt.values()){
            if(opt.isRequired()){
                options.addRequiredOption(opt.getName(), opt.getLongOpt(), opt.hasArg(), opt.getDescription());
            }else{
                options.addOption(opt.getName(), opt.getLongOpt(), opt.hasArg(), opt.getDescription());
            }
        }

        for(String arg: args){
            if(arg.equals("-"+Opt.HELP.getName()) || arg.equals("--"+options.getOption(Opt.HELP.getName()).getLongOpt())){
                printHelp(options);
                exit(0);
            }
        }

        CommandLineParser parser = new DefaultParser();
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logger.severe("Failed to parse options.");
            printHelp(options);
            exit(1);
        }
    }

    private void printHelp(Options options) {
        HelpFormatter help = new HelpFormatter();
        help.setOptionComparator(null);
        help.printHelp(Loader.class.getName(), options, true);
    }

    private File getFile() {
        File file;
        try {
            URL url = Loader.class.getProtectionDomain().getCodeSource().getLocation();
            file = new File(new URL(url.toString()).toURI());
            if(file.getParentFile().getName().equals("lib")){
                file = file.getParentFile().getParentFile();
            } else {
                file = new File("").getAbsoluteFile();
            }
        } catch (SecurityException | NullPointerException | URISyntaxException | MalformedURLException e) {
            file = new File("").getAbsoluteFile();
        }
        String date = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date(System.currentTimeMillis()+getOffsetFromNtpServer()));
        file = new File(file, Constants.FILE_NAME_PREFIX+date+".csv");

        if(file.exists()) {
            file.delete();
        }
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return file;
    }

    private void prepareClients() {
        String broker = cmd.getOptionValue(Opt.BROKER.getName(), Opt.BROKER.getDefaultValue());
        int version = Integer.valueOf(cmd.getOptionValue(Opt.VERSION.getName(), Opt.VERSION.getDefaultValue()));
        int numPub = Integer.valueOf(cmd.getOptionValue(Opt.NUM_PUB.getName(), Opt.NUM_PUB.getDefaultValue()));
        int numSub = Integer.valueOf(cmd.getOptionValue(Opt.NUM_SUB.getName(), Opt.NUM_SUB.getDefaultValue()));
        int pubQos = Integer.valueOf(cmd.getOptionValue(Opt.PUB_QOS.getName(), Opt.PUB_QOS.getDefaultValue()));
        int subQos = Integer.valueOf(cmd.getOptionValue(Opt.SUB_QOS.getName(), Opt.SUB_QOS.getDefaultValue()));
        boolean shSub = cmd.hasOption(Opt.SH_SUB.getName());
        boolean retain = cmd.hasOption(Opt.RETAIN.getName());
        String topic = cmd.getOptionValue(Opt.TOPIC.getName(), Opt.TOPIC.getDefaultValue());
        int payloadSize = Integer.valueOf(cmd.getOptionValue(Opt.PAYLOAD.getName(), Opt.PAYLOAD.getDefaultValue()));
        int numMessage = Integer.valueOf(cmd.getOptionValue(Opt.NUM_MSG.getName(), Opt.NUM_MSG.getDefaultValue()));
        int pubInterval = Integer.valueOf(cmd.getOptionValue(Opt.INTERVAL.getName(), Opt.INTERVAL.getDefaultValue()));

        for(int i=0;i<numPub;i++){
            if(i == 0) {
                logger.info("Publishers start to connect.");
            }

            if(version==5){
                publishers.add(new Publisher(i, broker, pubQos, retain, topic, payloadSize, numMessage, pubInterval));
            }else{
                publishers.add(new PublisherV3(i, broker, pubQos, retain, topic, payloadSize, numMessage, pubInterval));
            }
        }

        for(int i=0;i<numSub;i++){
            if(i == 0) {
                logger.info("Subscribers start to connect.");
            }

            if(version==5){
                subscribers.add(new Subscriber(i, broker, subQos, shSub, topic));
            }else{
                subscribers.add(new SubscriberV3(i, broker, subQos, topic));
            }
        }
    }

    /**
     * Start measurement by running publishers.
     */
    private void startMeasurement() {
        // delay: Give ScheduledExecutorService time to setup scheduling.
        long delay = publishers.size();
        startTime = System.currentTimeMillis() + getOffsetFromNtpServer() + delay;
        startNanoTime = System.nanoTime() + delay * Constants.MILLISECOND_IN_NANO;
        lastRecvTime = startTime;

        for(IClient pub: publishers){
            pub.start(delay);
        }
    }

    private long getOffsetFromNtpServer() {
        String ntpServer = cmd.getOptionValue(Opt.NTP.getName(), Opt.NTP.getDefaultValue());
        long offset = 0;
        if(ntpServer != null) {
            logger.info("Getting time information from NTP server.");
            NTPUDPClient client = new NTPUDPClient();
            client.setDefaultTimeout(5000);
            InetAddress address = null;
            TimeInfo ti = null;
            try {
                address = InetAddress.getByName(ntpServer);
                client.open();
                ti = client.getTime(address);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(ti != null) {
                ti.computeDetails();
                offset = ti.getOffset();
                logger.info("Offset is "+offset+" milliseconds.");
            } else {
                logger.warning("Failed to get time information from NTP server.");
            }
        }

        return offset;
    }

    private void disconnectClients() {
        for(int i=0;i<publishers.size();i++){
            if(i == 0) {
                logger.info("Publishers start to disconnect.");
            }

            publishers.get(i).disconnect();
        }

        for(int i=0;i<subscribers.size();i++){
            if(i == 0) {
                logger.info("Subscribers start to disconnect.");
            }

            subscribers.get(i).disconnect();
        }
    }

    private void calcResult() {
        TreeMap<Integer, Integer> sendThroughputs = new TreeMap<>();
        TreeMap<Integer, Integer> recvThroughputs = new TreeMap<>();
        TreeMap<Integer, ArrayList<Integer>> latencies = new TreeMap<>();

        FileInputStream fis = null;
        InputStreamReader isr = null;
        BufferedReader br = null;
        try{
            fis = new FileInputStream(file);
            isr = new InputStreamReader(fis);
            br = new BufferedReader(isr);

            String str;
            while ((str = br.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(str, ",");
                long timestamp = Long.valueOf(st.nextToken());
                st.nextToken(); //client ID
                boolean isSendRecord = st.nextToken().equals("S") ? true : false;
                int latency = 0;
                if (st.hasMoreTokens()) {
                    latency = Integer.valueOf(st.nextToken());
                }

                int elapsedSecond = (int)((timestamp-startTime)/1000);
                if(isSendRecord) {
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

                    if(!latencies.containsKey(elapsedSecond)) {
                        latencies.put(elapsedSecond, new ArrayList<Integer>());
                    }
                    latencies.get(elapsedSecond).add(latency);
                }
            }

            br.close();
            isr.close();
            fis.close();
        } catch(IOException e){
            e.printStackTrace();
        } finally {
            try {
                if(br != null) br.close();
                if(isr != null) isr.close();
                if(fis != null) fis.close();
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        int rampup = Integer.valueOf(cmd.getOptionValue(Opt.RAMP_UP.getName(), Opt.RAMP_UP.getDefaultValue()));
        int rampdown = Integer.valueOf(cmd.getOptionValue(Opt.RAMP_DOWN.getName(), Opt.RAMP_DOWN.getDefaultValue()));

        trimTreeMap(sendThroughputs, rampup, rampdown);
        trimTreeMap(recvThroughputs, rampup, rampdown);
        trimTreeMap(latencies, rampup, rampdown);

        paddingTreeMap(sendThroughputs);
        paddingTreeMap(recvThroughputs);

        System.out.println("-----Publisher-----");
        printThroughput(sendThroughputs, true);
        System.out.println();
        System.out.println("-----Subscriber-----");
        printThroughput(recvThroughputs, false);

        int maxLt = 0;
        long sumLt = 0;
        int count = 0;
        for(ArrayList<Integer> latencyList: latencies.values()){
            for(int latency: latencyList){
                if(latency > maxLt) {
                    maxLt = latency;
                }
                sumLt += latency;
                count++;
            }
        }
        double aveLt = count>0 ? (double)sumLt/count : 0;

        System.out.println("Maximum latency[ms]: "+maxLt);
        System.out.println("Average latency[ms]: "+aveLt);
    }

    private void trimTreeMap(TreeMap<Integer, ?> map, int rampup, int rampdown) {
        if(map.size() == 0) {
            return;
        }
        int firstTime = map.firstKey();
        int lastTime = map.lastKey();
        Iterator<Integer> itr = map.keySet().iterator();
        while(itr.hasNext()){
            int time = itr.next();
            if(time < rampup+firstTime) {
                itr.remove();
            }else if(time > lastTime-rampdown){
                itr.remove();
            }
        }
    }

    private void paddingTreeMap(TreeMap<Integer, Integer> map) {
        if(map.size() == 0) {
            return;
        }
        for(int i=map.firstKey();i<map.lastKey()+1;i++) {
            if(!map.containsKey(i)) {
                map.put(i, 0);
            }
        }
    }

    private void printThroughput(TreeMap<Integer, Integer> throughputs, boolean forPublisher) {
        int maxTh = 0;
        int sumMsg = 0;
        for(int elapsedSecond: throughputs.keySet()){
            int th = throughputs.get(elapsedSecond);
            if(th > maxTh) {
                maxTh = th;
            }
            sumMsg += th;
        }

        double aveTh = throughputs.size()>0 ? (double)sumMsg/throughputs.size() : 0;
        System.out.println("Maximum throughput[msg/s]: "+maxTh);
        System.out.println("Average throughput[msg/s]: "+aveTh);
        if(forPublisher){
            System.out.println("Number of published messages: "+sumMsg);
        }else{
            System.out.println("Number of received messages: "+sumMsg);
        }

        System.out.print("Throughput[msg/s]: ");
        for(int elapsedSecond: throughputs.keySet()){
            System.out.print(throughputs.get(elapsedSecond));
            if(elapsedSecond<throughputs.lastKey()){
                System.out.print(", ");
            }
        }
        System.out.println();
    }

    public static void main(String[] args){
        new Loader(args);
    }
}
