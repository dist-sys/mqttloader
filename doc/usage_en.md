# MQTTLoader usage (v0.8.4)
MQTTLoader is a load testing tool (client tool) for MQTT.  
It supports both MQTT v5.0 and v3.1.1.  
From v0.8.0, it supports TLS authentication.

## 1. Environment requirements
MQTTLoader is available on Windows, MacOS, Ubuntu Linux or any platforms that supports Java.  
It requires Java SE 8 or later.  
Older versions might work, but are not tested.

## 2. Download and run
Download the archive file (zip or tar) from: https://github.com/dist-sys/mqttloader/releases  
Below is an example of downloading by using curl command.

```
$ curl -OL https://github.com/dist-sys/mqttloader/releases/download/v0.8.4/mqttloader-0.8.4.zip
```

By extracting it, you can get the following files.

```
mqttloader/
+-- bin/
    +-- mqttloader
    +-- mqttloader.bat
+-- lib/
+-- logging.properties
+-- mqttloader.conf
```

Scripts for executing MQTTLoader is in *bin* directory.  
*mqttloader.bat* is for Windows users, and *mqttloader* is for Linux etc. users.  
For example with Linux, you can run MQTTLoader by moving to *bin* directory and using the following command.

```
$ ./mqttloader
```

*mqttloader.conf* is the default configuration file.  
Detail for the configuration parameters is described later.  
Below is an example of configuration:

```
broker = <Broker's IP address or FQDN>
broker_port = <Broker's port number>
num_publishers = 1
num_subscribers = 1
num_messages = 10
```

With the above configuration, MQTTLoader run with one publisher and one subscriber where each publisher sends 10 messages.  
By default *mqttloader.conf* is used, but you can specify your own configuration file by using the `-c` option.

`$ ./mqttloader -c "/home/testuser/myconfig.conf"`

Hereafter, we assume that the default configuration file *mqttloader.conf* is used.
If you just want to quickly confirm how MQTTLoader works, using a public broker is an easy way.  
For example, a public MQTT broker provided by HiveMQ is used by the following configuration.  
(Please do not make a heavy load on public brokers.)

```
broker = broker.hivemq.com
broker_port = 1883
```

## 3. Parameteres of MQTTLoader
The following table shows the parameters which can be set in *mqttloader.conf*.

| Parameter | Mandatory | Default value | Description |
|:-----------|:------------:|:------------|:------------|
| broker | Yes | (none) | Broker's IP address or FQDN. <br>Ex. `broker = 127.0.0.1` |
| broker_port | No | 1883 (non-TLS)<br>8883 (TLS) | Broker's port number. <br>Ex. `broker_port = 1883` |
| mqtt_version | No | 5 | MQTT version. `3` for MQTT v3.1.1, and `5` for MQTT v5.0. |
| num_publishers | No | 1 | The number of publishers. All publishers send messages to a same topic. |
| num_subscribers | No | 1 | The number of subscribers. All subscribers are subscribe to a same topic. |
| qos_publisher | No | 0 | QoS level of publishers. <br>Valid values are 0/1/2. |
| qos_subscriber | No | 0 | QoS level of subscribers. <br>Valid values are 0/1/2. |
| shared_subscription | No | false | A flag for enabling shared subscription. You can specify `true` or `false`. Valid for only MQTT v5.0. <br>If it is enabled, a message is delivered to one of the subscribers.<br>Ex. `shared_subscription = true` |
| retain | No | false | A flag for enabling retained messages. You can specify `true` or `false`. |
| topic | No | mqttloader-test-topic | Topic name to be used. |
| payload | No | 20 | Payload size of messages to be published in bytes. It must be equal to or larger than 8. |
| num_messages | No | 100 | The number of messages sent by **each** publisher. |
| ramp_up | No | 0 | Ramp-up time in seconds. <br>See **4. How to read the results** for details. |
| ramp_down | No | 0 | Ramp-down time in seconds. <br>See **4. How to read the results** for details. |
| interval | No | 0 | Publish interval in microseconds. |
| subscriber_timeout | No | 5 | Timeout for receiving messages by subscribers in seconds. |
| exec_time | No | 60 | Maximum execution time for measurement in seconds. |
| log_level | No | INFO | Log level. <br>Valid values are `SEVERE`/`WARNING`/`INFO`/`ALL`. |
| ntp | No | (none) | NTP server's IP address or FQDN. By setting this, throughput and latency are calculated based on the NTP server's time.<br>It should be set when running multiple MQTTLoader on different machines.<br>Ex. `ntp = ntp.nict.jp` |
| output <sup>**\*1 \*2**</sup> | No | (none) | Directory path to write out measurement record. If not set, MQTTLoader runs by in-memory mode. <br>Ex. `output = /home/testuser` |
| user_name | No | (none) | User name. Required if the broker has the configuration of password authentication. |
| password | No | (none) | Password. Required if the broker has the configuration of password authentication. |
| tls | No | false | A flag for enabling TLS authentication. You can specify `true` or `false`. |
| tls_rootca_cert <sup>**\*1**</sup> | No | (none) | File path of the root CA's certificate (PEM format). If the certificate has already been stored in your trust store ("cacerts" file in the Java installation directory), you do not need to specify this parameter. <br>Ex. `tls_rootca_cert = /home/testuser/rootca.crt`  |
| tls_client_key <sup>**\*1**</sup> | No | (none) | File path of the client's private key (PEM format). By specifying this parameter, TLS client authentication is enabled. <br>Ex. `tls_client_key = /home/testuser/client.key` |
| tls_client_cert_chain <sup>**\*1**</sup> | No | (none) | File paths of the client's certificate chain (PEM format). Multiple paths must be separated by semicolons. Be sure not to include semicolons in file names or directory names. The order must be from the client to the intermediate-CA(s). Root CA certificate is not necessarily. <br>Ex. `tls_client_cert_chain = /home/testuser/client.crt;/home/testuser/ica.crt` |

<sup>**\*1**</sup> For Windows, path separator ` \ ` must be escaped, e.g., `output = C:\\Users\\testuser\\outDir`.

<sup>**\*2**</sup> When setting `output` parameter, long term execution leads to huge file size. For more details, please refer to **4. How to read the results > Send/Receive record file**.

### Measurement time
MQTTLoader starts to terminate when all of the following conditions are met.  
- All publishers complete to send out messages.
- The time specified by `subscriber_timeout` elapses from the last time subscribers receive a message.

MQTTLoader also starts to terminate when the time specified by `exec_time` elapses, even if there are in-flight messages.  
Thus, if you want to test fixed number of messages by using `num_messages`, `exec_time` should be long sufficiently.  
If you want to do measurement with fixed time period by using `exec_time`, `num_messages` should be sufficiently large.

### Run on multiple machines
You can run MQTTLoader on multiple machines.  
Running both publishers and subscribers on a single machine may cause mutual influence, e.g., the subscribers' receiving load lowers the publishers' throughput.  
By running publishers and subscribers separately on different machines, you can avoid such mutual influence.  

For example, on a host A, you can run MQTTLoader with the following configuration in *mqttloader.conf*:

```
broker = <IP>
broker_port = <PORT>
num_publishers = 0
num_subscribers = 1
subscriber_timeout = 20
ntp = <NTP-SERVER>
```

Subsequently, you can run another MQTTLoader on a host B with the following configuration in *mqttloader.conf*:

```
broker = <IP>
broker_port = <PORT>
num_publishers = 1
num_subscribers = 0
num_messages = 10
ntp = <NTP-SERVER>
```

By these, from the publisher on host B to the subscriber on the host A via the broker, MQTT messages are delivered.  
When running on multiple machines, the following parameters in *mqttloader.conf* should be considered.

- Specify the same NTP server on host A and host B with the parameter `ntp`.  
- Specify enough long timeout period with the parameter `subscriber_timeout`.  

The former is to improve the accuracy of latency calculation, whereas the latter is to avoid that the subscriber terminates by timeout before starting the publisher.  

### TLS authentication
MQTTLoader supports TLS authentication.  
By specifying the parameter `tls` in *mqttloader.conf*, TLS authentication is enabled.

You can specify the root CA certificate (self-signed certificate) of the broker's certificate chain with the parameter `tls_rootca_cert` in *mqttloader.conf*.  
If the root CA has already been stored in your trust store ("cacerts" file in the Java installation directory), you can use TLS authentication without specifying this parameter.

Below is an example procedure when using Mosquitto's public broker.

1. Download CA certificate (mosquitto.org.crt) from https://test.mosquitto.org/
2. Place the CA certificate in an appropriate directory. In this example, we assume to place it in `/home/testuser`.
3. Specify the following parameters in *mqttloader.conf*.<br>
`broker = test.mosquitto.org`<br>
`broker_port = 8883`<br>
`tls = true`<br>
`tls_rootca_cert = /home/testuser/mosquitto.org.crt`
4. Run MQTTLoader.

#### TLS client authentication
To enable client authentication, you need to specify the client's private key, certificate and CA certificate.

Below is an example procedure when using Mosquitto's public broker.

1. Enable TLS authentication by the above procedure.
2. Generate client key.<br>
`$ openssl genrsa -out client.key`
3. Generate CSR (Certificate Signing Request).<br>
`$ openssl req -out client.csr -key client.key -new`
4. Get client certificate (client.crt) by using the website:<br>
https://test.mosquitto.org/ssl/
5. Place the client's private key and certificate in an appropriate directory. In this example, we assume to place it in `/home/testuser`.
6. Specify the following parameters in *mqttloader.conf*.<br>
`broker = test.mosquitto.org`<br>
`broker_port = 8884`<br>
`tls = true`<br>
`tls_rootca_cert = /home/testuser/mosquitto.org.crt`<br>
`tls_client_key = /home/testuser/client.key`<br>
`tls_client_cert_chain = /home/testuser/client.crt`
7. Run MQTTLoader.

Note that you do not need to specify `mosquitto.org.crt` with the parameter `tls_client_cert_chain`, because it is a self-signed certificate (root CA certificate).  
If it was an intermediate CA, you could specify `tls_client_cert_chain` as follows:  
`tls_client_cert_chain = /home/testuser/client.crt;/home/testuser/mosquitto.org.crt`


## 4. How to read the results
### Summary to standard output
MQTTLoader displays results like the following on standard output.

```
-----Publisher-----
Maximum throughput [msg/s]: 18622
Average throughput [msg/s]: 16666.666
Number of published messages: 100000
Per second throughput [msg/s]: 11955, 16427, 18430, 18030, 18622, 16536

-----Subscriber-----
Maximum throughput [msg/s]: 18620
Average throughput [msg/s]: 16666.666
Number of received messages: 100000
Per second throughput [msg/s]: 11218, 16414, 18426, 18026, 18620, 17296
Maximum latency [ms]: 81.838
Average latency [ms]: 42.236
```
MQTTLoader counts the number of messages sent by publishers.  
If QoS level is set to 1 or 2, counting is done when receiving PUBACK or PUBCOMP respectively.

After completion, MQTTLoader calculates the maximum throughput, the average throughput, and the number of published messages.  
`Per second throughput [msg/s]` is the time series of throughputs per second.  

By using the parameters `ramp_up` and `ramp_down`, you can exclude the beginning and trailing data.  
If you set the following parameter settings for example, the beginning one second and the trailing one second are excluded.

```
ramp_up = 1
ramp_down = 1
```

For subscribers, throughputs are calculated as same as the above for the received messages.  
In addition, the maximum latency and the average latency are calculated.  
Latency is the required time from sending out by a publisher to receiving by a subscriber.  
Each message has a timestamp of sending out in its payload and the subscriber receives it calculates the latency.  

To calculate the latency accurately, the clocks of pubilshers and subscribers should be the same or synchronized.  
When running multiple MQTTLoader on different machines (e.g., publishers on a machine and subscriber on another), it is better to use `ntp` parameter.   
By using `ntp` parameter, MQTTLoader acquires time information from the specified NTP server and uses it for timestamps and calculation.

### Send/Receive record file
If the parameter `output` is set, MQTTLoader writes out the record of sending/receiving MQTT messages to a file.  
The file is CSV format and placed at the directory specified by `output`.
The file name is generated from the measurement start time.  
Note that if the specified directory doesn't exist, it is newly created.

The file `mqttloader_xxxxxxxx-xxxxxx.csv` has records like the following:

```
1599643916416823,ml-EeiE-p-00001,S,
1599643916416882,ml-EeiE-p-00000,S,
1599643916419123,ml-EeiE-s-00000,R,3165
1599643916422982,ml-EeiE-p-00001,S,
 :
 :
```

Each line, consists of comma-separeted values, indicates the following data.  
In the case that the event type is `R`, latency data follows.

```
timestamp (Unix time in microseconds), client ID, event type (S: send, R: receive), latency (in microseconds)
```

Although MQTTLoader outputs the measurement result to the console, you can use the above .csv file for further analysis.  
Note that the latency in the above file is in microseconds, whereas that in the console is in milliseconds with three digits after the decimal point.  
the parameters `ramp_up` and `ramp_down` do not affect this file.  

---
---

## 5. For developers
### 5-a. Build requirements
To build MQTTLoader, JDK and Gradle with the following versions are required.

| Software | Version |
|:-----------|:------------|
| JDK | 8 or later |
| Gradle | 6.6 or later |

Older versions might work, but are not tested.  
This repository has Gradle Wrapper, so that the required version of Gradle will be automatically installed when usin gradlew command in the following steps.

cf. [Version compatibility between Gradle and Java](https://docs.gradle.org/current/userguide/compatibility.html)

### 5-b. Download
Clone the MQTTLoader repository from GitHub: `$ git clone git@github.com:dist-sys/mqttloader.git`  
The structure of the directories/files is as follows:

```
mqttloader/
+-- doc/
+-- gradle/
+-- src/
+-- .gitignore
+-- build.gradle
+-- gradlew
+-- gradlew.bat
:
```

Hereafter, the name of the root directory, where the file `build.gradle` exists, is denoted as *\<ROOT_DIR\>*.

### 5-c. Build
Open a terminal software (e.g., xterm, command prompt, etc.) and you can build by the following gradlew command.
```
$ cd <ROOT_DIR>
$ ./gradlew build
```

For Windows users, please use `gradlew.bat` instead of `./gradlew`.  
When using gradlew command first time, it may take time because the required version of Gradle is automatically downloaded.  
If successful, *build* directory is created under *\<ROOT_DIR\>*.
You can find *distributions* directory under the *build* directory.  

```
<ROOT_DIR>/
+-- build/
    +-- distributions/
        +-- mqttloader.tar
        +-- mqttloader.zip
```
By extracting the archive file (tar or zip), you can get the binary files of MQTTLoader.

### 5-d. Run MQTTLoader with Gradle
By using the following gradlew command in *\<ROOT_DIR\>*, you can run MQTTLoader.

`$ ./gradlew run`
