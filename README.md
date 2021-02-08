# MQTTLoader
MQTTLoader is a load testing tool (client tool) for MQTT.  
It supports both MQTT v5.0 and v3.1.1, as well as TLS authentication is available.

- [Usage (English)](https://github.com/dist-sys/mqttloader/blob/master/doc/usage_en.md)
- [Usage (日本語)](https://github.com/dist-sys/mqttloader/blob/master/doc/usage_jp.md)

Below is an execution result sample.

```
-----Publisher-----
Maximum throughput[msg/s]: 53068
Average throughput[msg/s]: 49894.57
Number of published messages: 349262
Per second throughput[msg/s]: 44460, 47558, 52569, 53068, 51041, 51583, 48983

-----Subscriber-----
Maximum throughput[msg/s]: 53050
Average throughput[msg/s]: 49891.14
Number of received messages: 349238
Per second throughput[msg/s]: 44399, 47587, 52566, 53050, 51078, 51575, 48983
Maximum latency[ms]: 24
Average latency[ms]: 1.39
```

MQTTLoader is licensed under the Apache License, Version2.0.

## Publications
- R. Banno, K. Ohsawa, Y. Kitagawa, T. Takada, T. Yoshizawa, "Measuring Performance of MQTT v5.0 Brokers with MQTTLoader", IEEE Consumer Communications & Networking Conference (CCNC), January 2021. (Demo paper)

## Contact
https://www.banno-lab.net/en/contact/

We welcome enquiries for research collaborations.
