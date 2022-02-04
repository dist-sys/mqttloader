# MQTTLoader
MQTTLoader is a load testing tool (client tool) for MQTT v5.0 and v3.1.1.  

- [Usage (English)](https://github.com/dist-sys/mqttloader/blob/master/doc/usage_en.md)
- [Usage (日本語)](https://github.com/dist-sys/mqttloader/blob/master/doc/usage_jp.md)

Below is an execution result sample.

```
-----Publisher-----
Maximum throughput [msg/s]: 53068
Average throughput [msg/s]: 49894.571
Number of published messages: 349262
Per second throughput [msg/s]: 44460, 47558, 52569, 53068, 51041, 51583, 48983

-----Subscriber-----
Maximum throughput [msg/s]: 53050
Average throughput [msg/s]: 49891.142
Number of received messages: 349238
Per second throughput [msg/s]: 44399, 47587, 52566, 53050, 51078, 51575, 48983
Maximum latency [ms]: 24.812
Average latency [ms]: 1.396
```

MQTTLoader is licensed under the Apache License, Version2.0.

## Related publications
- R. Banno, T. Yoshizawa, "A scalable IoT data collection method by shared-subscription with distributed MQTT brokers", EAI International Conference on Mobile Networks and Management (MONAMI), October 2021. [pdf](https://www.rbanno.net/data/paper/202110_EAI_MONAMI.pdf)
- R. Banno, K. Ohsawa, Y. Kitagawa, T. Takada, T. Yoshizawa, "Measuring Performance of MQTT v5.0 Brokers with MQTTLoader", IEEE Consumer Communications & Networking Conference (CCNC), January 2021. (Demo paper) [pdf](https://www.rbanno.net/data/paper/202101_IEEE_CCNC.pdf)

## Contact
https://www.banno-lab.net/en/contact/

We welcome inquiries for research collaborations.
