# MQTTLoader usage
MQTTLoader is a load testing tool (client tool) for MQTT.  
It supports both MQTT v5.0 and v3.1.1.

## 1. Environment requirements
MQTTLoader is available on Windows, MacOS, Ubuntu Linux or any platforms that supports Java.  
It requires Java SE 14.0.1 or later.  
Older versions might work, but are not tested.

## 2. Download and run
Download the archive file (zip or tar) from: https://github.com/dist-sys/mqttloader/releases  
By extracting it, you can get the following files.

```
mqttloader
+-- bin
    +-- mqttloader
    +-- mqttloader.bat
+-- lib
```

Scripts for executing MQTTLoader is in *bin* directory.  
`mqttloader.bat` is for Windows users, and `mqttloader` is for Linux etc. users.
You can display the help by:

`$ ./mqttloader -h`

```
usage: mqttloader.Loader -b <arg> [-v <arg>] [-p <arg>] [-s <arg>] [-pq
       <arg>] [-sq <arg>] [-ss] [-r] [-t <arg>] [-d <arg>] [-m <arg>] [-ru
       <arg>] [-rd <arg>] [-i <arg>] [-st <arg>] [-et <arg>] [-l <arg>]
       [-n <arg>] [-tf <arg>] [-lf <arg>] [-h]
 -b,--broker <arg>        Broker URL. E.g., tcp://127.0.0.1:1883
 -v,--version <arg>       MQTT version ("3" for 3.1.1 or "5" for 5.0).
  :
  :
```

For example, you can run MQTTLoader with one publisher that sends 10 messages and one subscriber by:

`$ ./mqttloader -b tcp://<IP>:<PORT> -p 1 -s 1 -m 10`

If you just want to quickly confirm how MQTTLoader works, using a public broker is a easy way.  
For example, the following command uses a public MQTT broker provided by HiveMQ.  
(Please do not make a haevy load on public brokers.)

`$ ./mqttloader -b tcp://broker.hivemq.com:1883 -p 1 -s 1 -m 10`

### Run on multiple machines
You can run MQTTLoader on multiple machines.  
Running both publishers and subscribers on a single machine may cause mutual influence, e.g., the subscribers' receiving load lowers the publishers' throughput.  
By running publishers and subscribers separately on different machines, you can avoid such mutual influence.  

For example, on a host A, you can run MQTTLoader as follows:

`$ ./mqttloader -b tcp://<IP>:<PORT> -p 0 -s 1 -st 20 -n <NTP-SERVER>`

Subsequently, you can run another MQTTLoader on a host B as follows:

`$ ./mqttloader -b tcp://<IP>:<PORT> -p 1 -s 0 -m 10 -n <NTP-SERVER>`

By these, from the publisher on host B to the subscriber on the host A via the broker, MQTT messages are delivered.  
When running on multiple machines, the following parameter settings should be considered.

- Specify the same NTP server on host A and host B with the parameter `-n`.  
- Specify enough long timeout period with the parameter `-st`.  

The former is to improve the accuracy of latency calculation, whereas the latter is to avoid that the subscriber terminates by timeout before starting the publisher.  
Please refer to **3. Parameteres of MQTTLoader** for more details of each parameter.

## 3. Parameteres of MQTTLoader

| Parameter | Default value | Description |
|:-----------|:------------|:------------|
| -b \<arg\> | (none) | Mandatory parameter. URL of the broker. <br>Ex. `tcp://127.0.0.1:1883` |
| -v \<arg\> | 5 | MQTT version. `3` for MQTT v3.1.1, and `5` for MQTT v5.0. |
| -p \<arg\> | 1 | Number of publishers. All publishers send messages to a same topic. |
| -s \<arg\> | 1 | Number of subscribers. All subscribers are subscribe to a same topic. |
| -pq \<arg\> | 0 | QoS level of publishers. <br>Valid values are 0/1/2. |
| -sq \<arg\> | 0 | QoS level of subscribers. <br>Valid values are 0/1/2. |
| -ss |  | Enable shared subscription. By default, it is disabled. Valid for only MQTT v5.0. <br>If it is enabled, a message is delivered to one of the subscribers. |
| -r |  | Enable retain for the messages sent by publishers. By default, it is disabled. |
| -t \<arg\> | mqttloader-test-topic | Topic name to be used. |
| -d \<arg\> | 20 | The size of data (payload of messages to be published) in bytes. |
| -m \<arg\> | 100 | Number of messages sent by **each** publisher. |
| -ru \<arg\> | 0 | Ramp-up time in seconds. <br>See **4. How to read the results** for details. |
| -rd \<arg\> | 0 | Ramp-down time in seconds. <br>See **4. How to read the results** for details. |
| -i \<arg\> | 0 | Publish interval in milliseconds. |
| -st \<arg\> | 5 | Timeout for receiving messages by subscribers in seconds. |
| -et \<arg\> | 60 | Maximum execution time for measurement in seconds. |
| -l \<arg\> | WARNING | Log level. <br>Valid values are `SEVERE`/`WARNING`/`INFO`/`ALL`. |
| -n \<arg\> | (none) | URL of the NTP server. By setting this, time synchronization is enabled. <br>Ex. `ntp.nict.jp` |
| -tf \<arg\> | (none) | File name to write out the throughput data. By default, file output is disabled. |
| -lf \<arg\> | (none) | File name to write out the latency data. By default, file output is disabled. |
| -h |  | Display help. |

MQTTLoader starts to terminate when all of the following conditions are met.  
- All publishers complete to send out messages.
- The time specified by the parameter `-st` elapses from the last time subscribers receive a message.

MQTTLoader also starts to terminate when the time specified by the parameter `-et` elapses, even if there are in-flight messages.  
Thus, `-et` should be long sufficiently.

If you want to do measurement with fixed time period, you can set the measurement time by the parameter `-et`.  
Note that you need to set sufficiently large value to the parameter `-m`.

By setting the parameter `-n`, MQTTLoader obtains the offset time from the specified NTP server and reflects it to calculate throughput and latency.  
It might be useful for running multiple MQTTLoader on different machines.  

## 4. How to read the results
### Summary to standard output
MQTTLoader displays results like the following on standard output.

```
-----Publisher-----
Maximum throughput[msg/s]: 18622
Average throughput[msg/s]: 16666.666666666668
Number of published messages: 100000
Throughput[msg/s]: 11955, 16427, 18430, 18030, 18622, 16536

-----Subscriber-----
Maximum throughput[msg/s]: 18620
Average throughput[msg/s]: 16666.666666666668
Number of received messages: 100000
Throughput[msg/s]: 11218, 16414, 18426, 18026, 18620, 17296
Maximum latency[ms]: 81
Average latency[ms]: 42.23691
```
For each publisher, MQTTLoader counts the number of messages sent for each second.  
If QoS level is set to 1 or 2, counting is done when receiving PUBACK or PUBCOMP respectively.

After completion, MQTTLoader collects the counted numbers from all publishers and calculates the maximum throughput, the average throughput, and the number of published messages.  
`Throughput[msg/s]` is the list of throughputs, which are the sum of each second for all publishers.  
Note that these calculation exclude the beginning and trailing seconds that have 0 messages.  
Below is an example of calculating throughputs in the case that two publishers, A and B, send messages.

| Elapsed seconds from starting measurement | # of meessages from A | # of messages from B | Throughputs |
|:-----------|:------------|:------------|:------------|
| 0 | 0 | 0 | Excluded |
| 1 | 3 | 0 | 3 |
| 2 | 4 | 3 | 7 |
| 3 | 5 | 5 | 10 |
| 4 | 0 | 0 | 0 |
| 5 | 3 | 4 | 7 |
| 6 | 2 | 2 | 4 |
| 7 | 0 | 0 | Excluded |
| 8 | 0 | 0 | Excluded |

By using the parameterse `-ru` and `-rd`, you can further exclude the beginning and trailing data.  
If you set `-ru 1 -rd 1` in the above example, the following data is used.

| Elapsed seconds from starting measurement | # of meessages from A | # of messages from B | Throughputs |
|:-----------|:------------|:------------|:------------|
| 2 | 4 | 3 | 7 |
| 3 | 5 | 5 | 10 |
| 4 | 0 | 0 | 0 |
| 5 | 3 | 4 | 7 |

For subscribers, throughputs are calculated as same as the above for the received messages.  
In addition, the maximum latency and the average latency are calculated.  
Latency is the required time from sending out by a publisher to receiving by a subscriber.  
Each message has a timestamp of sending out in its payload and the subscriber receives it calculates the latency.  
To calculate the latency accurately, the clocks of pubilshers and subscribers should be the same or synchronized.  
Thus, when running multiple MQTTLoader on different machines (e.g., publishers on a machine and subscriber on another), enabling `-n` parameter can improve the calculation of latency. 

### Data to file
By specifying the file name with `-tf` parameter, you can obtain throughput data like the following.

```
Measurement start time: 2020-09-01 18:33:38.122 JST
Measurement end time: 2020-09-01 18:33:54.104 JST
SLOT, mqttloaderclient-pub000000, mqttloaderclient-sub000000
0, 11955, 11218
1, 16427, 16414
2, 18430, 18426
3, 18030, 18026
4, 18622, 18620
5, 16536, 17296
```
This indicates the throughput for each second for each publisher.  
The data that used to calculate the summary data in the standard output is written out.

By specifying the file name with `-lf` parameter, you can obtain latency data like the following.

```
Measurement start time: 2020-09-01 18:33:38.122 JST
Measurement end time: 2020-09-01 18:33:54.104 JST
mqttloaderclient-sub000000, mqttloaderclient-sub000001
7, 7
4, 4
3, 3
4, 4
3, 4
3, 3
4, 4
3, 4
```
This indicates the latency for each message for each subscriber.

---
---

## 5. For developers
### 5-a. Build requirements
To build MQTTLoader, JDK and Gradle with the following versions are required.

| Software | Version |
|:-----------|:------------|
| JDK | 14.0.1 or later |
| Gradle | 4.9 or later |

Older versions might work, but are not tested.

### 5-b. Download
Clone the MQTTLoader repository from GitHub: `$ git clone git@github.com:dist-sys/mqttloader.git`  
The structure of the directories/files is as follows:

```
mqttloader
+-- docs
+-- src
+-- build.gradle
+-- logging.properties
:
```

Hereafter, the name of the root directory, where the file `build.gradle` exists, is denoted as *\<ROOT_DIR\>*.

### 5-c. Build
Open a terminal software (e.g., xterm, command prompt, etc.) and you can build by the following Gradle command.
```
$ cd <ROOT_DIR>
$ gradle build
```

If successful, *build* directory is created under *\<ROOT_DIR\>*.
You can find *distributions* directory under the *build* directory.  

```
<ROOT_DIR>
+-- build
    +-- distributions
        +-- mqttloader.tar
        +-- mqttloader.zip
```
By extracting the archive file (tar or zip), you can get the binary files of MQTTLoader.

### 5-d. Run MQTTLoader with Gradle
You can run MQTTLoader by using Gradle command.

In *\<ROOT_DIR\>/build.gradle*, the execution parameters are stated in the following part:

```
run {
    args '-h'.split('\\s+')
}
```

For example, by specifying like the following, MQTTLoader will use one publisher and one subscriber, and the publisher will send 10 messages.

```
run {
    args '-b tcp://<IP>:<PORT> -p 1 -s 1 -m 10'.split('\\s+')
}
```

Then, by using the following Gradle command in *\<ROOT_DIR\>*, you can run MQTTLoader.

`$ gradle run`
