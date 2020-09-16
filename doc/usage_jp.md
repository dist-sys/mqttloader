# MQTTLoader 利用方法 (v0.7.2)
MQTTLoaderは、MQTT v5.0とv3.1.1に対応した負荷テストツール（クライアントツール）です。

## 1. 環境要件
MQTTLoader は Java を利用可能なOS（Windows, MacOS, Ubuntu Linux等）上で動きます。  
Java SE 8以降で動作します。（より古いバージョンでの動作は未確認です。）

## 2. ダウンロード＆実行
以下のURLからアーカイブファイル（zip or tar）をダウンロードできます。

https://github.com/dist-sys/mqttloader/releases

ダウンロードしたファイルを解凍すると、以下のディレクトリ構造が得られます。

```
mqttloader/
+-- bin/
    +-- mqttloader
    +-- mqttloader.bat
+-- lib/
+-- logging.properties
```

*bin* に入っているのがMQTTLoaderの実行スクリプトです。  
Windowsユーザはmqttloader.bat（バッチファイル）を、Linux等のユーザはmqttloader（シェルスクリプト）を使います。  
このスクリプトを以下のように実行すると、ヘルプが表示されます。

`$ ./mqttloader -h`

```
MQTTLoader version 0.7.0
usage: mqttloader.Loader -b <arg> [-v <arg>] [-p <arg>] [-s <arg>] [-pq
       <arg>] [-sq <arg>] [-ss] [-r] [-t <arg>] [-d <arg>] [-m <arg>] [-ru
       <arg>] [-rd <arg>] [-i <arg>] [-st <arg>] [-et <arg>] [-l <arg>]
       [-n <arg>] [-im] [-h]
 -b,--broker <arg>        Broker URL. E.g., tcp://127.0.0.1:1883
 -v,--version <arg>       MQTT version ("3" for 3.1.1 or "5" for 5.0).
  :
  :
```

例えば以下のように実行すると、MQTTLoader は publisher と subscriber をひとつずつ立ち上げ、publisher からは10個のメッセージが送信（PUBLISH）されます。

`$ ./mqttloader -b tcp://<IP>:<PORT> -p 1 -s 1 -m 10`

MQTTLoaderの動作を確認するだけなら、パブリックブローカを使うのが手軽です。  
例えば、以下のように実行すると、HiveMQが提供しているパブリックブローカに接続することができます。  
（高い負荷をかけるような使い方にならないよう、注意してください。）

`$ ./mqttloader -b tcp://broker.hivemq.com:1883 -p 1 -s 1 -m 10`

**MQTTLoaderは、デフォルトで送受信レコードをファイル出力します。長時間実行した場合、ファイルサイズが大きくなることがありますので、注意してください。**  
ファイルを出力しないインメモリモードで動かすことも可能です。  
詳細は **4. 測定結果の見方 > 送受信レコードファイル** を参照してください。

### 複数台での実行
複数台のマシン上でMQTTLoaderを動かすこともできます。  

1台のマシン上でpublisherとsubscriberを動かした場合、subscriberの受信負荷によってpublisherの送信スループットが低下する等の可能性があります。  
publisherとsubscriberを別マシンで動かすことで、負荷が相互に影響することを避けることができます。

例えば、ホストA上で以下のように実行します。

`$ ./mqttloader -b tcp://<IP>:<PORT> -p 0 -s 1 -st 20 -n <NTP-SERVER>`

続いて、ホストB上で以下のように実行します。

`$ ./mqttloader -b tcp://<IP>:<PORT> -p 1 -s 0 -m 10 -n <NTP-SERVER>`

これにより、ホストB上のpublisherから、ホストA上のsubscriberへ、ブローカを経由してメッセージが流れます。  
複数台で実行する場合、以下のパラメータ設定に留意してください。

- `-n` にて同じNTPサーバを指定すること  
- `-st` にてsubscriberの受信タイムアウト時間を十分に長くとること  

前者はレイテンシ計算の正確性を上げるため、後者はpublisher側のプログラムを実行する前にsubscriberがタイムアウトしてしまうことを防ぐため、です。  
各パラメータの詳細については **3. MQTTLoaderのパラメータ** を参照してください。

## 3. MQTTLoaderのパラメータ

| パラメータ | デフォルト値 | 説明 |
|:-----------|:------------|:------------|
| -b \<arg\> | （無し） | 指定必須。ブローカのURL。<br>例：`tcp://127.0.0.1:1883` |
| -v \<arg\> | 5 | MQTTバージョン。 `3` を指定するとMQTT v3.1.1、`5` を指定するとMQTT v5.0。 |
| -p \<arg\> | 1 | publisher数。2以上の場合、全publisherは同じトピックにメッセージを送信。 |
| -s \<arg\> | 1 | subscriber数。2以上の場合、全subscriberは同じトピックをsubscribe。 |
| -pq \<arg\> | 0 | publisherが送信するメッセージのQoSレベル。<br>設定可能な値：0/1/2 |
| -sq \<arg\> | 0 | subscriber側のQoSレベル。<br>設定可能な値：0/1/2 |
| -ss |  | Shared subscriptionを有効にするかどうか（デフォルト：無効）。MQTT v5.0でのみ設定可。<br>有効にすると、各メッセージは全subscriberのうちいずれかひとつに届く。 |
| -r |  | publisherの送信メッセージにてRetainを有効にするかどうか（デフォルト：無効）。 |
| -t \<arg\> | mqttloader-test-topic | 測定で用いられるトピック名 |
| -d \<arg\> | 20 | publisherが送信するメッセージのデータサイズ（MQTTパケットのペイロードサイズ）。単位はbyte。設定可能な最小値は8。 |
| -m \<arg\> | 100 | **各**publisherによって送信されるメッセージの数。 |
| -ru \<arg\> | 0 | ランプアップ時間。単位は秒。<br>詳細は **4. 測定結果の見方** を参照。 |
| -rd \<arg\> | 0 | ランプダウン時間。単位は秒。<br>詳細は **4. 測定結果の見方** を参照。 |
| -i \<arg\> | 0 | 各publisherがメッセージを送信する間隔。単位はミリ秒。 |
| -st \<arg\> | 5 | subscriberの受信タイムアウト。単位は秒。 |
| -et \<arg\> | 60 | 測定の実行時間上限。単位は秒。 |
| -l \<arg\> | INFO | ログレベル。<br>設定可能な値：`SEVERE`/`WARNING`/`INFO`/`ALL` |
| -n \<arg\> | （無し） | NTPサーバのURL。設定すると時刻同期が有効になる（デフォルト：無効）。<br>例：`ntp.nict.jp`　 |
| -im \<arg\> | （無し） | MQTTLoaderをメモリ上でのみ動作させる。デフォルトでは、測定レコードはファイルに書き出される。 |
| -h |  | ヘルプを表示 |

MQTTLoaderは、以下の条件をすべて満たすと、クライアントを切断させ終了します。  
- 全publisherがメッセージ送信を完了
- 全subscriberのメッセージ受信のうち、最後の受信からパラメータ`-st`で指定した秒数が経過

また、MQTTLoaderは、パラメータ`-et`によって指定される時間が経過すると、メッセージ送受信中であっても、終了します。  
**一定数のメッセージ送受信**をテストしたい場合は、`-et`は長めに設定しておくと良いでしょう。  
**一定時間の測定**を行いたい場合には、`-et`を用いて測定時間を設定し、`-m`には十分大きな値を設定します。

パラメータ`-n`を設定すると、MQTTLoaderは指定されたNTPサーバから時刻のオフセット情報（NTPサーバ時刻からのずれ）を取得し、スループットやレイテンシの計算にそれを反映します。  
複数のMQTTLoaderを異なるマシン上で実行する場合に、利用を検討してください。

## 4. 測定結果の見方
### 標準出力のサマリ情報
MQTTLoadは標準出力に以下のような測定結果の情報を出力します。

```
-----Publisher-----
Maximum throughput[msg/s]: 18622
Average throughput[msg/s]: 16666.666666666668
Number of published messages: 100000
Per second throughput[msg/s]: 11955, 16427, 18430, 18030, 18622, 16536

-----Subscriber-----
Maximum throughput[msg/s]: 18620
Average throughput[msg/s]: 16666.666666666668
Number of received messages: 100000
Per second throughput[msg/s]: 11218, 16414, 18426, 18026, 18620, 17296
Maximum latency[ms]: 81
Average latency[ms]: 42.23691
```

MQTTLoaderは、各publisherによるメッセージの送信をカウントします。  
QoSレベルが1または2の場合は、それぞれ、PUBACKおよびPUBCOMPを受信したタイミングでカウントされます。

測定が終了したら、MQTTLoaderはカウントしたメッセージ数を集計し、最大スループット、平均スループット、送信メッセージ数を計算します。    
`Per second throughput[msg/s]`は、スループット値の時間変化を秒単位で列挙したものです。  

パラメータ`-ru`と`-rd`を用いると、測定開始直後と終了直前の一定秒数分を、集計対象データから除外することができます。    
例えば、 `-ru 1 -rd 1` と設定した場合、最初と最後の1秒間のデータは集計対象外となります。

subscriberに関しても、上記と同様にして、受信メッセージのスループットが計算されます。  
これに加えて、subscriber側では、最大レイテンシと平均レイテンシも計算されます。  
レイテンシは、publisherが送信したメッセージがsubscriberに届くまでの時間です。  
各メッセージはペイロード部に送信時刻を格納しており、subscriberは受信時にそれを用いてレイテンシの計算をおこないます。  

レイテンシを正確に算出するためには、publisherとsubscriberの時刻が同期されている必要があります。  
このため、複数の異なるマシン上でMQTTLoaderを動かす場合（例えば、publisherとsubscriberを別マシンで動かす場合）には、注意が必要です。  
`-n`パラメータを使うと、MQTTLoaderはNTPサーバから時刻情報を取得し、その情報をもとに送受信時刻やレイテンシを計算するため、マシンの時刻がずれていても（ある程度）正確なレイテンシを得られます。

### 送受信レコードファイル
デフォルトでは、MQTTLoaderはMQTTメッセージの送受信記録をファイルに出力します。  
以下のように、 `mqttloader` ディレクトリの直下に、csv形式のファイルとして出力されます。  
ファイル名は測定開始日時から生成されます。  
なお、GradleやIDEから実行した場合には、作業ディレクトリにファイルが作成されます。

```
mqttloader/
+-- bin/
    +-- mqttloader
    +-- mqttloader.bat
+-- lib/
+-- logging.properties
+-- mqttloader_xxxxxxxx-xxxxxx.csv
```

このcsvファイルには、以下のようなデータが記録されます。

```
1599643916416,ml-EeiE-p-00001,S,
1599643916416,ml-EeiE-p-00000,S,
1599643916419,ml-EeiE-s-00000,R,3
1599643916422,ml-EeiE-p-00001,S,
 :
 :
```

各行は、カンマ区切りで、以下の内容となっています。  
送受信種別が `R` の場合のみ、レイテンシも記載されます。

```
タイムスタンプ（Unix時間）, クライアントID, 送受信種別（S: 送信, R: 受信）, レイテンシ
```

MQTTLoaderは、測定結果のサマリをコンソールに出力しますが、追加の集計・分析を行いたい場合には上記のファイルを使ってください。  
なお、ファイル出力の負荷を抑えて測定をおこないたい場合には、 `-im` パラメータによりインメモリモードで動作させることができます。  
 `-im` パラメータを指定した場合、上記のcsvファイルは作成されません。

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
gradlewコマンドを使ってMQTTLoaderを実行することもできます。

*\<ROOT_DIR\>/build.gradle* 内の以下の箇所に、実行時オプションが記述されています。

```
run {
    args '-h'.split('\\s+')
}
```

例えば、以下のように指定することで、MQTTLoader は publisher と subscriber をひとつずつ立ち上げ、publisher からは10個のメッセージが送信（PUBLISH）されます。

```
run {
    args '-b tcp://<IP>:<PORT> -p 1 -s 1 -m 10'.split('\\s+')
}
```

上記のように *build.gradle* にオプションを記述した上で、 *\<ROOT_DIR\>* にて以下のgradlewコマンドを実行することで、MQTTLoaderを実行できます。 

`$ ./gradlew run`
