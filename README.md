# wso2 esb kafka connector

Based on original kafka-producer: https://github.com/wso2/esb-connectors/commit/1987421ee7e115342817e686afde929276cbaa68
https://github.com/wso2/esb-connectors/tree/master/kafka-producer

#### Difference between original and this version:
1. has fast build-in iterator
2. xpath support
3. backward compatible

### build-in iterator
Iterates the message in a similar way as xpath expression $body/\*[1]/\*.
Super fast - it provides thousands of messages per second, compared to the standard usage over iterate \ aggregate mediators. The downside - it is a necessity to form the message before sending it to the Kafka broker.

### build-in iterator examples
simple proxy
```xml
<?xml version="1.0" encoding="UTF-8"?>
<proxy xmlns="http://ws.apache.org/ns/synapse"
       name="testKafka"
       transports="https http"
       startOnLoad="true"
       trace="disable">
   <description/>
   <target>
      <inSequence>
         <kafkaTransport.init>
            <brokerList>localhost:9092</brokerList>
            <producerType>async</producerType>
            <batchNoMessages>500</batchNoMessages>
            <requiredAck>-1</requiredAck>
         </kafkaTransport.init>
         <kafkaTransport.publishMessages>
            <topic>test</topic>
            <autoiterate>on</autoiterate>
         </kafkaTransport.publishMessages>
      </inSequence>
   </target>
</proxy>
```
example message (structure is important in this case)
```xml
<!-- Example 1: -->
<x:Envelope xmlns:x="http://schemas.xmlsoap.org/soap/envelope/">
    <x:Body>
        <entry>
            <item name="test1"/>
            <item name="test2"/>
            <!-- ... -->
            <item name="test9999"/>
        </entry>
    </x:Body>
</x:Envelope>

<!-- Example 2: -->
<x:Envelope xmlns:x="http://schemas.xmlsoap.org/soap/envelope/">
    <x:Body>
        <library>
            <book>
                <author>George Orwell</author>
                <title>Nineteen Eighty-Four</title>
            </book>
            <book>
                <author>Yevgeny Zamyatin</author>
                <title>We</title>
            </book>
        </library>
    </x:Body>
</x:Envelope>
```
in kafka you'll see this:
```sh
### Example 1
# bin/kafka-console-consumer.sh --zookeeper localhost:2181 --topic test
<item name="test1"/>
<item name="test2"/>
...
<item name="test9999"/>

### Example 2
# bin/kafka-console-consumer.sh --zookeeper localhost:2181 --topic test
<book>
    <author>George Orwell</author>
    <title>Nineteen Eighty-Four</title>
</book>
<book>
    <author>Yevgeny Zamyatin</author>
    <title>We</title>
</book>
```
### xpath support
if the speed is not so important - then this method is for you. It still faster then iterate\aggregate but way slower than build-in iterator.
```xml
<?xml version="1.0" encoding="UTF-8"?>
<proxy xmlns="http://ws.apache.org/ns/synapse"
       name="testKafka"
       transports="https,http"
       statistics="disable"
       trace="disable"
       startOnLoad="true">
   <target>
      <inSequence>
         <kafkaTransport.init>
            <brokerList>localhost:9092</brokerList>
            <producerType>async</producerType>
            <batchNoMessages>500</batchNoMessages>
            <requiredAck>-1</requiredAck>
         </kafkaTransport.init>
         <kafkaTransport.publishMessages>
            <topic>test</topic>
            <autoiterate>on</autoiterate>
            <messageXPath>//v:domelement/z:test/documents/document|//library/book/author</messageXPath>
            <namespaces>z::http://rzrbld.ru/soap/z/;v::http://rzrbld.ru/soap/v/</namespaces>
         </kafkaTransport.publishMessages>
      </inSequence>
   </target>
   <description/>
</proxy>
```
simple message:
```xml
<!-- Example 1: -->
<x:Envelope xmlns:x="http://schemas.xmlsoap.org/soap/envelope/" xmlns:z="http://rzrbld.ru/soap/z/" xmlns:v="http://rzrbld.ru/soap/v/">
    <x:Body>
        <v:domelement>
            <z:test>
                <documents>
                    <document name="test1"/>
                    <document name="test2"/>
                    <document name="test3"/>
                </documents>
            </z:test>
        </v:domelement>
        <library>
            <book>
                <author>George Orwell</author>
                <title>Nineteen Eighty-Four</title>
            </book>
            <book>
                <author>Yevgeny Zamyatin</author>
                <title>We</title>
            </book>
        </library>
    </x:Body>
</x:Envelope>

```
what we got in kafka
```sh
# bin/kafka-console-consumer.sh --zookeeper localhost:2181 --topic test
<item name="test1"/>
<item name="test2"/>
<item name="test3"/>
<author>George Orwell</author>
<author>Yevgeny Zamyatin</author>
```

#### Build pre-requisites:
 * Maven 3.x
 * Java 1.7 or above

#### Tested Platform:
 - Ubuntu 15.10, Windows 10, CentOS 6.7
 - WSO2 ESB 4.8.1
 - Java 1.7, OpenJDK 7

#### build
```sh
$ cd /path/to/project
$ mvn clean install
```

### License
licensed according to the terms of Apache License, Version 2. ( http://www.apache.org/licenses/LICENSE-2.0 )
