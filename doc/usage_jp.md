# MQTTLoader 利用方法

## 1. 環境要件
MQTTLoader は Java を利用可能なOS（Windows, MacOS, Ubuntu Linux等）上で動きます。  
Java SE 14.0.1 以降での動作を確認しています。（それより前のバージョンでの動作は未確認です）

## 2. ダウンロード＆実行
以下のURLからアーカイブファイル（zip or tar）をダウンロードできます。

https://github.com/dist-sys/mqttloader/releases

ダウンロードしたファイルを解凍すると、以下のディレクトリ構造が得られます。

```
mqttloader
+-- bin
    +-- mqttloader
    +-- mqttloader.bat
+-- lib
```

*bin* に入っているのがMQTTLoaderの実行スクリプトです。  
Windowsユーザはmqttloader.bat（バッチファイル）を、Linux等のユーザはmqttloader（シェルスクリプト）を使います。  
このスクリプトを以下のように実行すると、ヘルプが表示されます。

`$ ./mqttloader -h`

```
usage: mqttloader.Loader -b <arg> [-v <arg>] [-p <arg>] [-s <arg>] [-pq
       <arg>] [-sq <arg>] [-ss] [-r] [-t <arg>] [-d <arg>] [-m <arg>] [-i
       <arg>] [-e <arg>] [-l <arg>] [-n <arg>] [-tf <arg>] [-lf <arg>]
       [-h]
 -b,--broker <arg>     Broker URL. E.g., tcp://127.0.0.1:1883
 -v,--version <arg>    MQTT version ("3" for 3.1.1 or "5" for 5.0).
 -p,--npub <arg>       Number of publishers.
 -s,--nsub <arg>       Number of subscribers.
 -pq,--pubqos <arg>    QoS level of publishers (0/1/2).
 -sq,--subqos <arg>    QoS level of subscribers (0/1/2).
 -ss,--shsub           Enable shared subscription.
 -r,--retain           Enable retain.
 -t,--topic <arg>      Topic name to be used.
 -d,--payload <arg>    Data (payload) size in bytes.
 -m,--nmsg <arg>       Number of messages sent by each publisher.
 -i,--interval <arg>   Publish interval in milliseconds.
 -e,--time <arg>       Execution time in seconds.
 -l,--log <arg>        Log level (SEVERE/WARNING/INFO/ALL).
 -n,--ntp <arg>        NTP server. E.g., ntp.nict.jp
 -tf,--thfile <arg>    File name for throughput data.
 -lf,--ltfile <arg>    File name for latency data.
 -h,--help             Display help.
```

例えば以下のように実行すると、MQTTLoader は publisher と subscriber をひとつずつ立ち上げ、publisher からは10個のメッセージが送信（PUBLISH）されます。

`$ ./mqttloader -b tcp://<IP>:<PORT> -p 1 -s 1 -m 10`


## 3. MQTTLoaderのパラメータ

| パラメータ | デフォルト値 | 説明 |
|:-----------|:------------|:------------|
| -b \<arg\> | （無し） | 指定必須。ブローカのURL。例：`tcp://127.0.0.1:1883` |
| -v \<arg\> | 5 | MQTTバージョン。 `3` を指定するとMQTT v3.1.1、`5` を指定するとMQTT v5.0が用いられる。 |
| -p \<arg\> | 10 | publisher数。2以上の場合、全publisherが同じトピックにメッセージを送信する。 |
| -s \<arg\> | 0 | subscriber数。2以上の場合、全subscriberが同じトピックをsubscribeする。 |
| -pq \<arg\> | 0 | publisherが送信するメッセージのQoSレベル。設定可能な値：0/1/2 |
| -sq \<arg\> | 0 | subscriber側のQoSレベル。設定可能な値：0/1/2 |
| -ss |  | Shared subscriptionのオン/オフ。このオプションを付けるとオン。デフォルトではオフ。MQTT v5.0でのみ有効。オンになっていると、各メッセージは全subscriberのうちいずれかひとつに届く。 |
| -r |  | publisherが送信するメッセージにおいてRetainを有効にするかどうか。このオプションを付けるとオン。デフォルトではオフ。 |
| -t \<arg\> | mqttloader-test-topic | 測定で用いられるトピック名 |
| -d \<arg\> | 1024 | publisherが送信するメッセージのデータサイズ（MQTTパケットのペイロード部分のサイズ）。単位はbyte。 |
| -m \<arg\> | 100 | **各**publisherによって送信されるメッセージの数。 |
| -i \<arg\> | 0 | 各publisherがメッセージを送信する間隔。単位はミリ秒。各publisherは、前のメッセージの送信が完了後、ここで指定された時間が経過してから、次のメッセージを送信する。 |
| -e \<arg\> | 10 | 測定の実行時間。単位は秒。 |
| -l \<arg\> | WARNING | ログレベル。設定可能な値：`SEVERE`/`WARNING`/`INFO`/`ALL` |
| -n \<arg\> | （無し） | NTPサーバのURL。例：`ntp.nict.jp`　（デフォルトでは、時刻同期はオフ） |
| -tf \<arg\> | （無し） | スループットデータを記録するファイル名。デフォルトではファイルへの記録はオフ。 |
| -lf \<arg\> | （無し） | レイテンシデータを記録するファイル名。デフォルトではファイルへの記録はオフ。 |
| -h |  | ヘルプを表示 |

パラメータ`-e`によって指定される時間は、タイムアウトの値です。  
MQTTLoaderは、publisherがメッセージの送信を開始してから、指定された時間が経過したら、クライアントを切断させ終了していきます。  
この値が短すぎると、メッセージの送信や受信が全て完了しない可能性があるため、注意してください。  
なお、subscriber数が0の場合、MQTTLoaderは、全publisherがメッセージ送信を完了したら即座にクライアントの終了をおこないます。

パラメータ`-n`は、複数のMQTTLoaderを異なるマシン上で実行する場合に役に立つかもしれません。  
このパラメータを設定すると、MQTTLoaderは指定されたNTPサーバから時刻のオフセット情報を取得し、スループットやレイテンシの計算にそれを反映します。

## 4. 測定結果の見方
### 標準出力のサマリ情報
MQTTLoadは標準出力に以下のような測定結果の情報を出力します。

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

MQTTLoaderは、各publisherごとに、毎秒の送信メッセージ数をカウントします。  
全てのメッセージ送信が完了したら、MQTTLoaderは全publisherからカウントしたメッセージ数の情報を集めて集計し、最大スループット、平均スループット、送信メッセージ数を計算します。  
`Throughput[msg/s]`の項は、スループット値の列挙です。列挙されているそれぞれの値は、各秒における全publisherの送信メッセージ数を足し合わせたものです。  
なお、測定開始時および終了時に送信メッセージ数が0の期間がある場合は、スループットの計算からは除外されます。  
ふたつのpublisher AとBがメッセージを送信する場合の、スループット集計値の例を以下に示します。

| 測定開始からの秒数 | Aの送信メッセージ数 | Bの送信メッセージ数 | スループット集計値 |
|:-----------|:------------|:------------|:------------|
| 0 | 0 | 0 | 集計対象外 |
| 1 | 3 | 0 | 3 |
| 2 | 4 | 3 | 7 |
| 3 | 5 | 5 | 10 |
| 4 | 0 | 0 | 0 |
| 5 | 3 | 4 | 7 |
| 6 | 2 | 2 | 4 |
| 7 | 0 | 0 | 集計対象外 |
| 8 | 0 | 0 | 集計対象外 |

subscriberに関しても、上記と同様にして、受信メッセージのスループットが計算されます。  
これに加えて、subscriber側では、最大レイテンシと平均レイテンシも計算されます。  
レイテンシは、publisherが送信したメッセージがsubscriberに届くまでの時間です。  
各メッセージはペイロード部に送信時刻を格納しており、subscriberは受信時にそれを用いてレイテンシの計算をおこないます。  
レイテンシを正確に算出するためには、publisherとsubscriberの時刻が同期されている必要があります。  
このため、複数の異なるマシン上でMQTTLoaderを動かす場合（例えば、publisherとsubscriberを別マシンで動かす場合）には、注意が必要です。  
`-n`パラメータを使うことで、レイテンシ計算の正確性を改善できる可能性があります。

### ファイル出力
パラメータ`-tf`でファイル名を指定することで、以下のようなスループットの詳細データをファイルに書き出すことができます。
```
SLOT, mqttloaderclient-pub000000, mqttloaderclient-sub000000
0, 11955, 11218
1, 16427, 16414
2, 18430, 18426
3, 18030, 18026
4, 18622, 18620
5, 16536, 17296
```
これは、各秒における、各publisherのスループット（送信メッセージ数）を表しています。  
標準出力のサマリ情報と同様、測定開始時および終了時に送信メッセージ数が0の期間がある場合は、スループットの計算からは除外されます。 seconds that have 0 messages.

パラメータ`-lf`でファイル名を指定することで、以下のようなレイテンシの詳細データをファイルに書き出すことができます。
```
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
これは、各subscriberが受信した各メッセージのレイテンシを表しています。

---
---

## 5. 開発者向け
### 5-a. ビルド要件
MQTTLoaderのビルドには、以下バージョンのJDKとGradleが必要です。

| Software | Version |
|:-----------|:------------|
| JDK | 14.0.1 or later |
| Gradle | 4.9 or later |

上記より前のバージョンでの動作は未確認です。

## 5-b. ダウンロード
GitHubからクローンしてください： `$ git clone git@github.com:dist-sys/mqttloader.git`  
リポジトリのディレクトリ構造は下記のようになっています。

```
mqttloader
+-- docs
+-- src
+-- build.gradle
+-- logging.properties
:
```

以降、ルートディレクトリ（`build.gradle`が置いてあるディレクトリ）を *\<ROOT_DIR\>* と表記します。

## 5-c. ビルド
ターミナル（xterm、コマンドプロンプト等）で以下のように *\<ROOT_DIR\>* に移動してコマンドを打つことで、ビルドできます。 

```
$ cd <ROOT_DIR>
$ gradle build
```

成功すると、*\<ROOT_DIR\>* 配下に *build* ディレクトリが生成されます。

```
<ROOT_DIR>
+-- build
    +-- distributions
        +-- mqttloader.tar
        +-- mqttloader.zip
```

*distributions* ディレクトリに入っているアーカイブファイル（tar または zip）を解凍することで、MQTTLoaderのバイナリが得られます。

## 5-d. GradleによるMQTTLoaderの実行
Gradleコマンドを使ってMQTTLoaderを実行することもできます。

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

上記のように *build.gradle* にオプションを記述した上で、 *\<ROOT_DIR\>* にて以下のGradleコマンドを実行することで、MQTTLoaderを実行できます。 

`$ gradle run`
