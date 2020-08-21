package mqttloader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import mqttloader.client.IPublisher;
import mqttloader.client.ISubscriber;
import mqttloader.client.Publisher;
import mqttloader.client.PublisherV3;
import mqttloader.client.Subscriber;
import mqttloader.client.SubscriberV3;
import mqttloader.record.Throughput;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

public class Loader {
    private String broker;
    private int version;
    private int numPub;
    private int numSub;
    private int pubQos;
    private int subQos;
    private boolean shSub;
    private boolean retain;
    private String topic;
    private int payloadSize;    // In bytes. Minimum 8 bytes.
    private int numMessage; // Per client.
    private int rampup; // In seconds.
    private int rampdown; // In seconds.
    private int pubInterval;    // In milliseconds.
    private int subTimeout; // In seconds.
    private int execTime; // In seconds.
    private String logLevel;  // SEVERE/WARNING/INFO/ALL
    private String ntpServer;
    private String thFile;
    private String ltFile;

    private ArrayList<IPublisher> publishers = new ArrayList<>();
    private ArrayList<ISubscriber> subscribers = new ArrayList<>();
    public static long startTime;
    public static long offset = 0;
    public static long lastRecvTime;
    public static CountDownLatch countDownLatch;
    public static Logger logger = Logger.getLogger(Loader.class.getName());

    public enum Opt {
        BROKER("b"),
        VERSION("v"),
        NUM_PUB("p"),
        NUM_SUB("s"),
        PUB_QOS("pq"),
        SUB_QOS("sq"),
        SH_SUB("ss"),
        RETAIN("r"),
        TOPIC("t"),
        PAYLOAD("d"),
        NUM_MSG("m"),
        RAMP_UP("ru"),
        RAMP_DOWN("rd"),
        INTERVAL("i"),
        SUB_TIMEOUT("st"),
        EXEC_TIME("et"),
        LOG_LEVEL("l"),
        NTP("n"),
        TH_FILE("tf"),
        LT_FILE("lf"),
        HELP("h");

        private String name;

        private Opt(String name) {
            this.name = name;
        }
    }

    public Loader(String[] args) {
        setOptions(args);
        if(logLevel.equals("SEVERE")) logger.setLevel(Level.SEVERE);
        if(logLevel.equals("WARNING")) logger.setLevel(Level.WARNING);
        if(logLevel.equals("INFO")) logger.setLevel(Level.INFO);
        if(logLevel.equals("ALL")) logger.setLevel(Level.ALL);

        logger.info("Starting mqttloader tool.");
        logger.info("Preparing clients.");
        prepareClients();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        logger.info("Starting measurement.");
        startMeasurement();

        Timer timer = new Timer();
        if(numSub > 0){
            timer.schedule(new RecvTimeoutTask(timer, subTimeout), subTimeout*1000);
        }

        try {
            countDownLatch.await(execTime, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        timer.cancel();

        logger.info("Terminating clients.");
        disconnectClients();

        logger.info("Printing results.");
        printResult();
        if(thFile!=null) thToFile();
        if(ltFile!=null) ltToFile();
    }

    private Options defOptions() {
        Options options = new Options();
        options.addOption(Option.builder(Opt.BROKER.name)
                .longOpt("broker")
                .required()
                .hasArg()
                .desc("Broker URL. E.g., tcp://127.0.0.1:1883")
                .build());
        options.addOption(Opt.VERSION.name, "version", true, "MQTT version (\"3\" for 3.1.1 or \"5\" for 5.0).");
        options.addOption(Opt.NUM_PUB.name, "npub", true, "Number of publishers.");
        options.addOption(Opt.NUM_SUB.name, "nsub", true, "Number of subscribers.");
        options.addOption(Opt.PUB_QOS.name, "pubqos", true, "QoS level of publishers (0/1/2).");
        options.addOption(Opt.SUB_QOS.name, "subqos", true, "QoS level of subscribers (0/1/2).");
        options.addOption(Opt.SH_SUB.name, "shsub", false, "Enable shared subscription.");
        options.addOption(Opt.RETAIN.name, "retain", false, "Enable retain.");
        options.addOption(Opt.TOPIC.name, "topic", true, "Topic name to be used.");
        options.addOption(Opt.PAYLOAD.name, "payload", true, "Data (payload) size in bytes.");
        options.addOption(Opt.NUM_MSG.name, "nmsg", true, "Number of messages sent by each publisher.");
        options.addOption(Opt.RAMP_UP.name, "rampup", true, "Ramp-up time in seconds.");
        options.addOption(Opt.RAMP_DOWN.name, "rampdown", true, "Ramp-down time in seconds.");
        options.addOption(Opt.INTERVAL.name, "interval", true, "Publish interval in milliseconds.");
        options.addOption(Opt.SUB_TIMEOUT.name, "subtimeout", true, "Subscribers' timeout in seconds.");
        options.addOption(Opt.EXEC_TIME.name, "exectime", true, "Execution time in seconds.");
        options.addOption(Opt.LOG_LEVEL.name, "log", true, "Log level (SEVERE/WARNING/INFO/ALL).");
        options.addOption(Opt.NTP.name, "ntp", true, "NTP server. E.g., ntp.nict.jp");
        options.addOption(Opt.TH_FILE.name, "thfile", true, "File name for throughput data.");
        options.addOption(Opt.LT_FILE.name, "ltfile", true, "File name for latency data.");
        options.addOption(Opt.HELP.name, "help", false, "Display help.");

        return options;
    }

    private void setOptions(String[] args) {
        Options options = defOptions();

        for(String arg: args){
            if(arg.equals("-"+Opt.HELP.name) || arg.equals("--"+options.getOption(Opt.HELP.name).getLongOpt())){
                printHelp(options);
                System.exit(0);
            }
        }

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logger.severe("Failed to parse options.");
            printHelp(options);
            System.exit(1);
        }

        broker = cmd.getOptionValue(Opt.BROKER.name, null);
        version = Integer.valueOf(cmd.getOptionValue(Opt.VERSION.name, "5"));
        numPub = Integer.valueOf(cmd.getOptionValue(Opt.NUM_PUB.name, "10"));
        numSub = Integer.valueOf(cmd.getOptionValue(Opt.NUM_SUB.name, "0"));
        pubQos = Integer.valueOf(cmd.getOptionValue(Opt.PUB_QOS.name, "0"));
        subQos = Integer.valueOf(cmd.getOptionValue(Opt.SUB_QOS.name, "0"));
        shSub = cmd.hasOption(Opt.SH_SUB.name);
        retain = cmd.hasOption(Opt.RETAIN.name);
        topic = cmd.getOptionValue(Opt.TOPIC.name, "mqttloader-test-topic");
        payloadSize = Integer.valueOf(cmd.getOptionValue(Opt.PAYLOAD.name, "1024"));
        numMessage = Integer.valueOf(cmd.getOptionValue(Opt.NUM_MSG.name, "100"));
        rampup = Integer.valueOf(cmd.getOptionValue(Opt.RAMP_UP.name, "0"));
        rampdown = Integer.valueOf(cmd.getOptionValue(Opt.RAMP_DOWN.name, "0"));
        pubInterval = Integer.valueOf(cmd.getOptionValue(Opt.INTERVAL.name, "0"));
        subTimeout = Integer.valueOf(cmd.getOptionValue(Opt.SUB_TIMEOUT.name, "5"));
        execTime = Integer.valueOf(cmd.getOptionValue(Opt.EXEC_TIME.name, "60"));
        logLevel = cmd.getOptionValue(Opt.LOG_LEVEL.name, "WARNING");
        ntpServer = cmd.getOptionValue(Opt.NTP.name, null);
        thFile = cmd.getOptionValue(Opt.TH_FILE.name, null);
        ltFile = cmd.getOptionValue(Opt.LT_FILE.name, null);

        if (numSub > 0) {
            countDownLatch = new CountDownLatch(numPub+1);  // For waiting for publishers' completion and subscribers' timeout.
        } else {
            countDownLatch = new CountDownLatch(numPub);
        }
    }

    private void printHelp(Options options) {
        HelpFormatter help = new HelpFormatter();
        help.setOptionComparator(null);
        help.printHelp(Loader.class.getName(), options, true);
    }

    private void prepareClients() {
        for(int i=0;i<numPub;i++){
            if(version==5){
                publishers.add(new Publisher(i, broker, pubQos, retain, topic, payloadSize, numMessage, pubInterval));
            }else{
                publishers.add(new PublisherV3(i, broker, pubQos, retain, topic, payloadSize, numMessage, pubInterval));
            }
        }
        for(int i=0;i<numSub;i++){
            if(version==5){
                subscribers.add(new Subscriber(i, broker, subQos, shSub, topic));
            }else{
                subscribers.add(new SubscriberV3(i, broker, subQos, topic));
            }
        }
    }

    private void startMeasurement() {
        if(ntpServer != null) {
            logger.info("Getting time information from NTP server.");
            NTPUDPClient client = new NTPUDPClient();
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
            ti.computeDetails();
            offset = ti.getOffset();
            logger.info("Offset is "+offset+" milliseconds.");
        }

        startTime = getTime();
        lastRecvTime = startTime;
        for(IPublisher pub: publishers){
            pub.start();
        }
    }

    private void disconnectClients() {
        for(IPublisher pub: publishers){
            pub.disconnect();
        }
        for(ISubscriber sub: subscribers){
            sub.disconnect();
        }
    }

    private void printResult() {
        printPubResult();
        System.out.println();
        printSubResult();
    }

    private void printPubResult() {
        TreeMap<Integer, Integer> thTotal = new TreeMap<>();

        for(IPublisher pub: publishers){
            ArrayList<Throughput> pubth = pub.getThroughputs();
            for(Throughput th : pubth) {
                if(thTotal.containsKey(th.getSlot())){
                    thTotal.put(th.getSlot(), thTotal.get(th.getSlot())+th.getCount());
                }else{
                    thTotal.put(th.getSlot(), th.getCount());
                }
            }
        }

        for(int i=thTotal.firstKey(); i<=thTotal.lastKey(); i++){
            if(!thTotal.containsKey(i)){
                thTotal.put(i, 0);
            }
        }

        int maxTh = 0;
        int sumMsg = 0;
        int count = 0;
        for(int slot: thTotal.keySet()){
            if(slot < rampup || slot > thTotal.lastKey()-rampdown){
                continue;
            }

            int th = thTotal.get(slot);
            if(th > maxTh) maxTh = th;
            sumMsg += th;
            count++;
        }
        double aveTh = count>0 ? (double)sumMsg/count : 0;

        System.out.println("-----Publisher-----");
        System.out.println("Maximum throughput[msg/s]: "+maxTh);
        System.out.println("Average throughput[msg/s]: "+aveTh);
        System.out.println("Number of published messages: "+sumMsg);
        System.out.print("Throughput[msg/s]: ");
        for(int slot: thTotal.keySet()){
            if(slot < rampup || slot > thTotal.lastKey()-rampdown){
                continue;
            }

            System.out.print(thTotal.get(slot));
            if(slot<thTotal.lastKey()-rampdown){
                System.out.print(", ");
            }
        }
        System.out.println();
    }

    private void printSubResult() {
        TreeMap<Integer, Integer> thTotal = new TreeMap<>();

        for(ISubscriber sub: subscribers){
            ArrayList<Throughput> subth = sub.getThroughputs();
            for(Throughput th : subth) {
                if(thTotal.containsKey(th.getSlot())){
                    thTotal.put(th.getSlot(), thTotal.get(th.getSlot())+th.getCount());
                }else{
                    thTotal.put(th.getSlot(), th.getCount());
                }
            }
        }

        for(int i=thTotal.firstKey(); i<=thTotal.lastKey(); i++){
            if(!thTotal.containsKey(i)){
                thTotal.put(i, 0);
            }
        }

        int maxTh = 0;
        int sumMsg = 0;
        int count = 0;
        for(int slot: thTotal.keySet()){
            if(slot < rampup || slot > thTotal.lastKey()-rampdown){
                continue;
            }

            int th = thTotal.get(slot);
            if(th > maxTh) maxTh = th;
            sumMsg += th;
            count++;
        }
        double aveTh = count>0 ? (double)sumMsg/count : 0;

        int maxLt = 0;
        long sumLt = 0;
        for(ISubscriber sub: subscribers){
            for(int i=0;i<sub.getLatencies().size();i++){
                int slot = sub.getLatencies().get(i).getSlot();
                if(slot < rampup || slot > thTotal.lastKey()-rampdown){
                    continue;
                }

                int lt = sub.getLatencies().get(i).getLatency();
                if(lt > maxLt) maxLt = lt;
                sumLt += lt;
            }
        }
        double aveLt = sumMsg>0 ? (double)sumLt/sumMsg : 0;

        System.out.println("-----Subscriber-----");
        System.out.println("Maximum throughput[msg/s]: "+maxTh);
        System.out.println("Average throughput[msg/s]: "+aveTh);
        System.out.println("Number of received messages: "+sumMsg);
        System.out.print("Throughput[msg/s]: ");
        for(int slot: thTotal.keySet()){
            if(slot < rampup || slot > thTotal.lastKey()-rampdown){
                continue;
            }

            System.out.print(thTotal.get(slot));
            if(slot<thTotal.lastKey()-rampdown){
                System.out.print(", ");
            }
        }
        System.out.println();
        System.out.println("Maximum latency[ms]: "+maxLt);
        System.out.println("Average latency[ms]: "+aveLt);
    }

    private void thToFile(){
        StringBuilder sb = new StringBuilder();

        sb.append("SLOT");
        for(int i=0;i<publishers.size();i++){
            sb.append(", "+publishers.get(i).getClientId());
        }
        for(int i=0;i<subscribers.size();i++){
            sb.append(", "+subscribers.get(i).getClientId());
        }
        sb.append("\n");


        // slot, <pub-id, count>
        TreeMap<Integer, TreeMap<Integer, Integer>> thAggregate = new TreeMap<>();
        for(int i=0;i<publishers.size();i++){
            ArrayList<Throughput> pubth = publishers.get(i).getThroughputs();
            for(Throughput th: pubth) {
                if(!thAggregate.containsKey(th.getSlot())){
                    TreeMap<Integer, Integer> map = new TreeMap<>();
                    thAggregate.put(th.getSlot(), map);
                }
                thAggregate.get(th.getSlot()).put(i, th.getCount());
            }
        }
        for(int i=0;i<subscribers.size();i++){
            ArrayList<Throughput> subth = subscribers.get(i).getThroughputs();
            for(Throughput th: subth) {
                if(!thAggregate.containsKey(th.getSlot())){
                    TreeMap<Integer, Integer> map = new TreeMap<>();
                    thAggregate.put(th.getSlot(), map);
                }
                thAggregate.get(th.getSlot()).put(i+publishers.size(), th.getCount());
            }
        }

        int numClients = publishers.size()+subscribers.size();
        for(int slot=0;slot<thAggregate.lastKey()+1;slot++){
            StringBuilder lineSb = new StringBuilder();
            lineSb.append(slot);

            for(int i=0;i<numClients;i++) {
                if (thAggregate.containsKey(slot)) {
                    if (thAggregate.get(slot).containsKey(i)) {
                        lineSb.append(", " + thAggregate.get(slot).get(i));
                        continue;
                    }
                }
                lineSb.append(", " + 0);
            }
            lineSb.append("\n");
            sb.append(lineSb);
        }

        output(thFile, sb.toString(), false);
    }

    private void ltToFile(){
        StringBuilder sb = new StringBuilder();

        for(int i=0;i<subscribers.size();i++){
            if(i>0) sb.append(", ");
            sb.append(subscribers.get(i).getClientId());
        }
        sb.append("\n");

        int index = 0;
        while(true) {
            StringBuilder lineSb = new StringBuilder();
            boolean hasNext = false;
            for(int i=0;i<subscribers.size();i++){
                int lt = 0;
                if(subscribers.get(i).getLatencies().size()>index){
                    lt = subscribers.get(i).getLatencies().get(index).getLatency();
                    hasNext = true;
                }
                if(i>0) lineSb.append(", ");
                lineSb.append(lt);
            }
            lineSb.append("\n");
            if(hasNext){
                index++;
                sb.append(lineSb);
            }else{
                break;
            }
        }

        output(ltFile, sb.toString(), false);
    }

    public void output(String filename, String str, boolean append){
        File file = new File(filename);

        if(!file.exists() || file == null){
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        FileOutputStream fos = null;
        OutputStreamWriter osw = null;
        BufferedWriter bw = null;
        try{
            fos = new FileOutputStream(file, append);
            osw = new OutputStreamWriter(fos);
            bw = new BufferedWriter(osw);

            bw.write(str);

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

    public static byte[] genPayloads(int size) {
        return ByteBuffer.allocate(size).putLong(getTime()).array();
    }

    public static long getTime() {
        return System.currentTimeMillis() + offset;
    }

    public static void main(String[] args){
        new Loader(args);
    }
}
