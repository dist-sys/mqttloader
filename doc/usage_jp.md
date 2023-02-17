# MQTTLoader 利用方法 (v0.8.5)
MQTTLoaderは、MQTT v5.0とv3.1.1に対応した負荷テストツール（クライアントツール）です。  
v0.8.0から、ブローカとのTLS接続にも対応しました。

## 1. 環境要件
MQTTLoader は Java を利用可能なOS（Windows, MacOS, Ubuntu Linux等）上で動きます。  
Java SE 8以降で動作します。（より古いバージョンでの動作は未確認です。）

## 2. ダウンロード＆実行
以下のURLからアーカイブファイル（zip or tar）をダウンロードできます。

https://github.com/dist-sys/mqttloader/releases

以下は、Curlコマンドを使ってダウンロードする場合の例です。

```
$ curl -OL https://github.com/dist-sys/mqttloader/releases/download/v0.8.5/mqttloader-0.8.5.zip
```

ダウンロードしたファイルを解凍すると、以下のディレクトリ構造が得られます。

```
mqttloader/
+-- bin/
    +-- mqttloader
    +-- mqttloader.bat
+-- lib/
+-- logging.properties
+-- mqttloader.conf
```

*bin* に入っているのがMQTTLoaderの実行スクリプトです。  
Windowsユーザは *mqttloader.bat* （バッチファイル）を、Linux等のユーザは *mqttloader* （シェルスクリプト）を使います。  
例えばLinuxの場合、 *bin* ディレクトリに移動して以下コマンドを打つことで、MQTTLoaderを実行できます。

```
$ ./mqttloader
```

*mqttloader.conf* は設定ファイルです。  
詳細は後述しますが、例えば以下のように書きます。

```
broker = <ブローカのIPアドレス or FQDN>
broker_port = <ブローカのポート番号>
num_publishers = 1
num_subscribers = 1
num_messages = 10
```

上記設定内容の場合、MQTTLoader は publisher と subscriber をひとつずつ立ち上げ、publisher からは10個のメッセージが送信（PUBLISH）されます。  
デフォルトでは *mqttloader.conf* が使われますが、 `-c` オプションにて任意の場所にある設定ファイルを指定可能です。 

`$ ./mqttloader -c "/home/testuser/myconfig.conf"`

以降の説明は、デフォルトの設定ファイル *mqttloader.conf* を利用するものとして記述しています。
MQTTLoaderの動作を確認するだけなら、パブリックブローカを使うのが手軽です。  
例えば、 *mqttloader.conf* にて以下のようにブローカを指定すると、HiveMQが提供しているパブリックブローカに接続することができます。  
（高い負荷をかけるような使い方にならないよう、注意してください。）

```
broker = broker.hivemq.com
broker_port = 1883
```

## 3. MQTTLoaderのパラメータ
*mqttloader.conf* で設定できるパラメータの一覧を、以下に示します。

| パラメータ | 指定必須 | デフォルト値 | 説明 |
|:-----------|:------------:|:------------|:------------|
| broker | ○ | (無し) | ブローカのIPアドレスまたはFQDN。 <br>例： `broker = 127.0.0.1` |
| broker_port | × | 1883 (non-TLS)<br>8883 (TLS) | ブローカのポート番号。 <br>例： `broker_port = 1883` |
| mqtt_version | × | 5 | MQTTバージョン。 3 を指定するとMQTT v3.1.1、5 を指定するとMQTT v5.0。 |
| num_publishers | × | 1 | publisher数。全publisherは同じトピックにメッセージを送信。 |
| num_subscribers | × | 1 | subscriber数。全subscriberは同じトピックをsubscribe。 |
| qos_publisher | × | 0 | publisherのQoSレベル。<br>設定可能な値：0/1/2 |
| qos_subscriber | × | 0 | subscriberのQoSレベル。<br>設定可能な値：0/1/2 |
| shared_subscription | × | false | Shared subscriptionの有効/無効を指定するフラグ。指定可能な値は true/false 。MQTT v5.0でのみ設定可。<br>有効にすると、各メッセージは全subscriberのうちいずれかひとつに届く。<br>例： `shared_subscription = true` |
| retain | × | false | Retainの有効/無効を指定するフラグ。指定可能な値は true/false 。 |
| topic | × | mqttloader-test-topic | 測定で用いられるトピック名。 |
| payload | × | 20 | publisherが送信するメッセージのペイロードサイズ。単位はbyte。設定可能な最小値は8。 |
| num_messages | × | 100 | **各**publisherによって送信されるメッセージの数。 |
| ramp_up | × | 0 | ランプアップ時間。単位は秒。<br>詳細は **4. 測定結果の見方** を参照。 |
| ramp_down | × | 0 | ランプダウン時間。単位は秒。<br>詳細は **4. 測定結果の見方** を参照。 |
| interval | × | 0 | 各publisherがメッセージを送信する間隔。単位はマイクロ秒。<br>num_publishersの値とは関係なく、各publisherはそれぞれがintervalおきに送信する。また、複数publisher同士の送信タイミングは、可能な範囲でinterval/num_publishersだけずれるように調整される。 |
| subscriber_timeout | × | 5 | subscriberの受信タイムアウト。単位は秒。 |
| exec_time | × | 60 | 測定の実行時間上限。単位は秒。 |
| log_level | × | INFO | ログレベル。<br>設定可能な値：SEVERE/WARNING/INFO/ALL |
| ntp | × | (無し) | NTPサーバのIPアドレスまたはFQDN。設定すると、スループットやレイテンシの計算がNTPサーバ時刻を基準として行われる。<br>複数のMQTTLoaderを異なるマシン上で実行する場合には設定することが望ましい。<br>例：`ntp = ntp.nict.jp` |
| output <sup>**※1※2**</sup> | × | (無し) | 測定レコードを書き出すディレクトリのパス。未指定の場合、MQTTLoaderはメモリ上でのみ動作。 <br>例： `output = /home/testuser` |
| user_name | × | (無し) | ユーザ名（ブローカにてパスワード認証が設定されている場合に指定）。 |
| password | × | (無し) | パスワード（ブローカにてパスワード認証が設定されている場合に指定）。 |
| tls | × | false | TLS認証の有効/無効を指定するフラグ。指定可能な値は true/false 。 |
| tls_rootca_cert <sup>**※1**</sup> | × | (無し) | TLSサーバ認証用のルートCA証明書（PEM形式）のパス。Java実行環境が参照する信頼済み証明書ストア（通常、Javaインストールディレクトリの `cacerts` ファイル）に登録済みのルートCAであれば、指定不要。<br>例： `tls_rootca_cert = /home/testuser/rootca.crt`  |
| tls_client_key <sup>**※1**</sup> | × | (無し) | TLSクライアント認証用の秘密鍵（PEM形式）のパス。このパラメータを指定することで、TLSクライアント認証が有効になる。<br>例： `tls_client_key = /home/testuser/client.key` |
| tls_client_cert_chain <sup>**※1**</sup> | × | (無し) | TLSクライアント認証用のクライアント証明書（PEM形式）のパス。中間CA証明書（PEM形式）のパスもセミコロン区切りで記載する。クライアント証明書、中間CA証明書、の順序で記載すること。ルートCA証明書は不要。（ファイル名・フォルダ名にセミコロンが含まれていると利用不可）<br>例： `tls_client_cert_chain = /home/testuser/client.crt;/home/testuser/ica.crt` |

<sup>**※1**</sup> Windowsのファイルパスの区切り文字 ` \ ` はエスケープする必要があります。例えば、`output = C:\\Users\\testuser\\outDir` のように書く必要があります。

<sup>**※2**</sup> `output` を指定して長時間実行した場合、出力されるファイルのサイズが非常に大きくなることがあるため、注意してください。詳細は **4. 測定結果の見方 > 送受信レコードファイル** を参照してください。

### 測定終了までの時間について
MQTTLoaderは、以下の条件をすべて満たすと、クライアントを切断させ終了します。  
- 全publisherがメッセージ送信を完了
- 全subscriberのメッセージ受信のうち、最後の受信からパラメータ`subscriber_timeout`で指定した秒数が経過

また、MQTTLoaderは、パラメータ`exec_time`で指定された時間が経過すると、メッセージ送受信中であっても、終了します。  
**一定数のメッセージ送受信**をテストしたい場合は、`num_messages`でメッセージ数を設定し、`exec_time`を十分長めに設定します。  
**一定時間の測定**を行いたい場合には、`exec_time`を用いて測定時間を設定し、`num_messages`を十分大きな値に設定します。

### 複数台での実行
複数台のマシン上でMQTTLoaderを動かすことができます。  
1台のマシン上でpublisherとsubscriberを動かした場合、subscriberの受信負荷によってpublisherの送信スループットが低下する等の可能性があります。  
publisherとsubscriberを別マシンで動かすことで、負荷が相互に影響することを避けることができます。

例えば、ホストA上で、以下の設定でMQTTLoaderを実行します。

```
broker = <IP>
broker_port = <PORT>
num_publishers = 0
num_subscribers = 1
subscriber_timeout = 20
ntp = <NTP-SERVER>
```

続いて、ホストB上で以下の設定でMQTTLoaderを実行します。

```
broker = <IP>
broker_port = <PORT>
num_publishers = 1
num_subscribers = 0
num_messages = 10
ntp = <NTP-SERVER>
```

これにより、ホストB上のpublisherから、ホストA上のsubscriberへ、ブローカを経由してメッセージが流れます。  
複数台で実行する場合、 *mqttloader.conf* の以下のパラメータに留意してください。

- `ntp` にて同じNTPサーバを指定すること  
- `subscriber_timeout` にてsubscriberの受信タイムアウト時間を十分に長くとること  

前者はレイテンシ計算の正確性を上げるため、後者はpublisher側のプログラムを実行する前にsubscriberがタイムアウトしてしまうことを防ぐため、です。  

### TLS接続
MQTTLoaderは、ブローカとのTLS接続が可能です。  
*mqttloader.conf* の `tls` を `true` に設定することで、TLS接続が有効となります。

ブローカの証明書チェーンのルートCA証明書（自己署名証明書）を、*mqttloader.conf* の `tls_rootca_cert` で指定することができます。  
（信頼済み証明書ストアに登録済みのルートCAであれば、指定せずにそのままTLS接続ができます。通常、Java実行環境が参照する信頼済み証明書ストアは、Javaインストールディレクトリの `cacerts` ファイルです。）

以下、Mosquittoのパブリックブローカを例に、手順を述べます。

1. https://test.mosquitto.org/ から、CA証明書（mosquitto.org.crt）をダウンロード
2. CA証明書を、適切なディレクトリに配置。ここでは、 `/home/testuser` に置くと仮定する。
3. *mqttloader.conf* に、以下を指定<br>
`broker = test.mosquitto.org`<br>
`broker_port = 8883`<br>
`tls = true`<br>
`tls_rootca_cert = /home/testuser/mosquitto.org.crt`
4. MQTTLoaderを実行

#### TLSクライアント認証
クライアント認証もおこなう場合、上記に加え、クライアントの秘密鍵およびクライアント証明書・CA証明書の指定が必要です。

以下、Mosquittoのパブリックブローカを例に、手順を述べます。

1. 上記の手順により、TLSサーバ認証を有効状態にしておく
2. クライアント鍵を生成<br>
`$ openssl genrsa -out client.key`
3. 署名要求（CSR）を生成<br>
`$ openssl req -out client.csr -key client.key -new`
4. クライアント証明書の作成<br>
https://test.mosquitto.org/ssl/ にCSRを入力して、 client.crt をダウンロードする。
5. 秘密鍵およびクライアント証明書を適切なディレクトリに配置。ここでは、 `/home/testuser` に置くと仮定する。
6. *mqttloader.conf* に、以下を指定<br>
`broker = test.mosquitto.org`<br>
`broker_port = 8884`<br>
`tls = true`<br>
`tls_rootca_cert = /home/testuser/mosquitto.org.crt`<br>
`tls_client_key = /home/testuser/client.key`<br>
`tls_client_cert_chain = /home/testuser/client.crt`
7. MQTTLoaderを実行

`mosquitto.org.crt` は自己署名証明書（ルートCA証明書）であるため、`tls_client_cert_chain` での指定は不要です。  
もし、`mosquitto.org.crt` が中間CA証明書である場合は、`tls_client_cert_chain` は以下のように記述します。  
`tls_client_cert_chain = /home/testuser/client.crt;/home/testuser/mosquitto.org.crt`


## 4. 測定結果の見方
### 標準出力のサマリ情報
MQTTLoadは標準出力に以下のような測定結果の情報を出力します。

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

MQTTLoaderは、各publisherによるメッセージの送信をカウントします。  
QoSレベルが1または2の場合は、それぞれ、PUBACKおよびPUBCOMPを受信したタイミングでカウントされます。

測定が終了したら、MQTTLoaderはカウントしたメッセージ数を集計し、最大スループット、平均スループット、送信メッセージ数を計算します。    
`Per second throughput [msg/s]`は、スループット値の時間変化を秒単位で列挙したものです。  

パラメータ`ramp_up`と`ramp_down`を用いると、測定開始直後と終了直前の一定秒数分を、集計対象データから除外することができます。    
例えば以下のように設定した場合、最初と最後の1秒間のデータは集計対象外となります。

```
ramp_up = 1
ramp_down = 1
```

subscriberに関しても、上記と同様にして、受信メッセージのスループットが計算されます。  
これに加えて、subscriber側では、最大レイテンシと平均レイテンシも計算されます。  
レイテンシは、publisherが送信したメッセージがsubscriberに届くまでの時間です。  
各メッセージはペイロード部に送信時刻を格納しており、subscriberは受信時にそれを用いてレイテンシの計算をおこないます。  

レイテンシを正確に算出するためには、publisherとsubscriberの時刻が同期されている必要があります。  
このため、複数の異なるマシン上でMQTTLoaderを動かす場合（例えば、publisherとsubscriberを別マシンで動かす場合）には、注意が必要です。  
`ntp` パラメータを使うと、MQTTLoaderはNTPサーバから時刻情報を取得し、その情報をもとに送受信時刻やレイテンシを計算するため、マシンの時刻がずれていても（ある程度）正確なレイテンシを得られます。

### 送受信レコードファイル
`output` パラメータが指定されている場合、MQTTLoaderはMQTTメッセージの送受信記録をファイルに出力します。  
`output` で指定されたディレクトリの直下に、csv形式のファイルとして出力されます。  
ファイル名は測定開始日時から生成されます。  
なお、指定したディレクトリが存在しない場合は、新たにディレクトリが作成されます。

このcsvファイルには、以下のようなデータが記録されます。

```
1599643916401359,,,
1599643916416823,ml-EeiE-p-00001,S,
1599643916416882,ml-EeiE-p-00000,S,
1599643916419123,ml-EeiE-s-00000,R,3165
1599643916422982,ml-EeiE-p-00001,S,
 :
 :
```

各行は、カンマ区切りで、以下の内容となっています。  
先頭行には測定開始時刻のみが記載されます。  
また、送受信種別が `R` の場合のみ、レイテンシも記載されます。

```
タイムスタンプ（マイクロ秒単位Unix時間）, クライアントID, 送受信種別（S: 送信, R: 受信）, レイテンシ（マイクロ秒単位）
```

MQTTLoaderは、測定結果のサマリをコンソールに出力しますが、追加の集計・分析を行いたい場合には上記のファイルを使ってください。  
なお、コンソールに出力されるレイテンシはミリ秒単位（小数点以下3桁まで）であるのに対し、上記ファイルのレイテンシはマイクロ秒単位である点に注意してください。  
また、パラメータ`ramp_up`と`ramp_down`はこのファイルには影響しません（すべての送受信記録が出力されます）。  

---
---

## 5. 開発者向け
### 5-a. ビルド要件
MQTTLoaderのビルドには、以下バージョンのJDKとGradleが必要です。

| Software | Version |
|:-----------|:------------|
| JDK | 8 or later |
| Gradle | 6.6 or later |

上記より前のバージョンでの動作は未確認です。  
なお、Gradle wrapperを用意してあるため、以降の手順でgradlewコマンドを使う際に、Gradleは自動的にインストールされます。

参考：[GradleとJavaのバージョン互換性](https://docs.gradle.org/current/userguide/compatibility.html)

### 5-b. ダウンロード
GitHubからクローンしてください： `$ git clone git@github.com:dist-sys/mqttloader.git`  
リポジトリのディレクトリ構造は下記のようになっています。

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

以降、ルートディレクトリ（`build.gradle`が置いてあるディレクトリ）を *\<ROOT_DIR\>* と表記します。

### 5-c. ビルド
ターミナル（xterm、コマンドプロンプト等）で以下のように *\<ROOT_DIR\>* に移動してコマンドを打つことで、ビルドできます。 

```
$ cd <ROOT_DIR>
$ ./gradlew build
```

Windowsユーザは、 `./gradlew` の代わりに `gradlew.bat` を使ってください（以降も同じ）。  
gradlewコマンドを初めて実行する際には、Gradleが自動的にダウンロードされるため、少し時間がかかります。  
成功すると、*\<ROOT_DIR\>* 配下に *build* ディレクトリが生成されます。

```
<ROOT_DIR>/
+-- build/
    +-- distributions/
        +-- mqttloader.tar
        +-- mqttloader.zip
```

*distributions* ディレクトリに入っているアーカイブファイル（tar または zip）を解凍することで、MQTTLoaderのバイナリが得られます。

### 5-d. GradleによるMQTTLoaderの実行
*\<ROOT_DIR\>* にて以下のgradlewコマンドを実行することで、MQTTLoaderを実行できます。 

`$ ./gradlew run`
