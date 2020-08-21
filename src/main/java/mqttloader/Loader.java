package mqttloader;

import static mqttloader.Constants.Opt;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import mqttloader.client.IClient;
import mqttloader.client.Publisher;
import mqttloader.client.PublisherV3;
import mqttloader.client.Subscriber;
import mqttloader.client.SubscriberV3;
import mqttloader.record.Latency;
import mqttloader.record.Throughput;
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
    public static long startTime;
    public static long offset = 0;
    public static long lastRecvTime;
    public static CountDownLatch countDownLatch;
    public static Logger logger = Logger.getLogger(Loader.class.getName());

    public Loader(String[] args) {
        setOptions(args);

        int numPub = Integer.valueOf(cmd.getOptionValue(Opt.NUM_PUB.getName(), Opt.NUM_PUB.getDefaultValue()));
        int numSub = Integer.valueOf(cmd.getOptionValue(Opt.NUM_SUB.getName(), Opt.NUM_SUB.getDefaultValue()));
        if (numSub > 0) {
            countDownLatch = new CountDownLatch(numPub+1);  // For waiting for publishers' completion and subscribers' timeout.
        } else {
            countDownLatch = new CountDownLatch(numPub);
        }

        String logLevel = cmd.getOptionValue(Opt.LOG_LEVEL.getName(), Opt.LOG_LEVEL.getDefaultValue());
        logger.setLevel(Level.parse(logLevel));

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
            int subTimeout = Integer.valueOf(cmd.getOptionValue(Opt.SUB_TIMEOUT.getName(), Opt.SUB_TIMEOUT.getDefaultValue()));
            timer.schedule(new RecvTimeoutTask(timer, subTimeout), subTimeout*1000);
        }

        int execTime = Integer.valueOf(cmd.getOptionValue(Opt.EXEC_TIME.getName(), Opt.EXEC_TIME.getDefaultValue()));
        try {
            countDownLatch.await(execTime, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        timer.cancel();

        logger.info("Terminating clients.");
        disconnectClients();

        logger.info("Printing results.");
        dataCleansing();

        printThroughput(true);
        System.out.println();
        printThroughput(false);
        printLatency();

        String thFile = cmd.getOptionValue(Opt.TH_FILE.getName(), Opt.TH_FILE.getDefaultValue());
        String ltFile = cmd.getOptionValue(Opt.LT_FILE.getName(), Opt.LT_FILE.getDefaultValue());
        if(thFile!=null) thToFile();
        if(ltFile!=null) ltToFile();
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
                System.exit(0);
            }
        }

        CommandLineParser parser = new DefaultParser();
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logger.severe("Failed to parse options.");
            printHelp(options);
            System.exit(1);
        }
    }

    private void printHelp(Options options) {
        HelpFormatter help = new HelpFormatter();
        help.setOptionComparator(null);
        help.printHelp(Loader.class.getName(), options, true);
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
        String ntpServer = cmd.getOptionValue(Opt.NTP.getName(), Opt.NTP.getDefaultValue());
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

        startTime = Util.getTime();
        lastRecvTime = startTime;
        for(IClient pub: publishers){
            pub.start();
        }
    }

    private void disconnectClients() {
        for(IClient pub: publishers){
            pub.disconnect();
        }
        for(IClient sub: subscribers){
            sub.disconnect();
        }
    }

    private void dataCleansing() {
        int rampup = Integer.valueOf(cmd.getOptionValue(Opt.RAMP_UP.getName(), Opt.RAMP_UP.getDefaultValue()));
        int rampdown = Integer.valueOf(cmd.getOptionValue(Opt.RAMP_DOWN.getName(), Opt.RAMP_DOWN.getDefaultValue()));
        if(rampup==0 && rampdown==0) return;

        int pubFirstSlot = Integer.MAX_VALUE;
        int pubLastSlot = 0;
        for(IClient pub: publishers){
            ArrayList<Throughput> list = pub.getThroughputs();
            if(!list.isEmpty()) {
                int first = list.get(0).getSlot();
                int last = list.get(list.size()-1).getSlot();
                if(first < pubFirstSlot) pubFirstSlot = first;
                if(last > pubLastSlot) pubLastSlot = last;
            }
        }

        for(IClient pub: publishers) {
            Iterator<Throughput> itr = pub.getThroughputs().iterator();
            while(itr.hasNext()){
                Throughput th = itr.next();
                if(th.getSlot() < rampup+pubFirstSlot) {
                    itr.remove();
                }else if(th.getSlot() > pubLastSlot-rampdown){
                    itr.remove();
                }
            }
        }

        int subFirstSlot = Integer.MAX_VALUE;
        int subLastSlot = 0;
        for(IClient sub: subscribers){
            ArrayList<Throughput> list = sub.getThroughputs();
            if(!list.isEmpty()) {
                int first = list.get(0).getSlot();
                int last = list.get(list.size()-1).getSlot();
                if(first < subFirstSlot) subFirstSlot = first;
                if(last > subLastSlot) subLastSlot = last;
            }
        }

        for(IClient sub: subscribers){
            Iterator<Throughput> itrTh = sub.getThroughputs().iterator();
            while(itrTh.hasNext()){
                Throughput th = itrTh.next();
                if(th.getSlot() < rampup+subFirstSlot) {
                    itrTh.remove();
                }else if(th.getSlot() > subLastSlot-rampdown){
                    itrTh.remove();
                }
            }

            Iterator<Latency> itrLt = sub.getLatencies().iterator();
            while(itrLt.hasNext()){
                Latency lt = itrLt.next();
                if(lt.getSlot() < rampup+subFirstSlot) {
                    itrLt.remove();
                }else if(lt.getSlot() > subLastSlot-rampdown){
                    itrLt.remove();
                }
            }
        }
    }

    private void printThroughput(boolean forPub) {
        TreeMap<Integer, Integer> thTotal = new TreeMap<>();
        ArrayList<IClient> clients;
        if(forPub){
            clients = publishers;
        }else{
            clients = subscribers;
        }

        for(IClient client: clients){
            ArrayList<Throughput> ths = client.getThroughputs();
            for(Throughput th : ths) {
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
        for(int slot: thTotal.keySet()){
            int th = thTotal.get(slot);
            if(th > maxTh) {
                maxTh = th;
            }
            sumMsg += th;
        }
        double aveTh = thTotal.size()>0 ? (double)sumMsg/thTotal.size() : 0;

        if(forPub){
            System.out.println("-----Publisher-----");
        }else{
            System.out.println("-----Subscriber-----");
        }
        System.out.println("Maximum throughput[msg/s]: "+maxTh);
        System.out.println("Average throughput[msg/s]: "+aveTh);
        if(forPub){
            System.out.println("Number of published messages: "+sumMsg);
        }else{
            System.out.println("Number of received messages: "+sumMsg);
        }
        System.out.print("Throughput[msg/s]: ");
        for(int slot: thTotal.keySet()){
            System.out.print(thTotal.get(slot));
            if(slot<thTotal.lastKey()){
                System.out.print(", ");
            }
        }
        System.out.println();
    }

    private void printLatency() {
        int maxLt = 0;
        long sumLt = 0;
        int count = 0;
        for(IClient sub: subscribers){
            for(int i=0;i<sub.getLatencies().size();i++){
                int lt = sub.getLatencies().get(i).getLatency();
                if(lt > maxLt) maxLt = lt;
                sumLt += lt;
                count++;
            }
        }
        double aveLt = count>0 ? (double)sumLt/count : 0;

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
        for(int slot=thAggregate.firstKey();slot<thAggregate.lastKey()+1;slot++){
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

        String thFile = cmd.getOptionValue(Opt.TH_FILE.getName(), Opt.TH_FILE.getDefaultValue());
        Util.output(thFile, sb.toString(), false);
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

        String ltFile = cmd.getOptionValue(Opt.LT_FILE.getName(), Opt.LT_FILE.getDefaultValue());
        Util.output(ltFile, sb.toString(), false);
    }

    public static void main(String[] args){
        new Loader(args);
    }
}
