---
title: springcloudstream 集成kafka和rabbitmq
date: 2020-03-31 20:56:13
tags:
 - java
 - spring
 - springcloudstream
 - kafka
 - rabbitmq
categories: 
 - [spring,springcloudstream]
 - [kafka]
 - [rabbitmq]
---

## 前提

业务中不同消息中间件的切换，需要修改大量的代码，增加了业务开发者的负担。所以我们需要寻求一种解决方案用来兼容多种消息中间件，springcloudstream就是一个很好的选择，它提供许多可以简化消息驱动微服务应用程序编写的抽象和原语，目前springcloudstream支持的binder有RabbitMQ、Apache Kafka、Amazon Kinesis、Google PubSub等。在这里用springcloudstream编写简单的demo同时集成kafka和rabbitmq,验证消息中间件的可用性。

## 准备

创建一个springboot工程,并添加核心jar包:
``` java
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-stream-rabbit</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-stream-kafka</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream</artifactId>
</dependency>
```
<!-- more -->
其他依赖
``` java
    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-stream-test-support</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>Hoxton.SR1</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
```

## 代码实现

### yml配置

配置文件是spingcloudstream的核心，这样有利于消息组件的变更。

``` java
server:
  port: 18080

spring:
  application:
    name: msg-integrate
  cloud:
    stream:
      bindings:
        mq_output:     #自定义消息通道（默认output/input）
          binder: rabbit-demo  #指定要连接binders中kafka或rabbitmq
          destination: mq-dest  #kafka中的topic  rabbit中的exchange
          content-type: text/plain  #消息发送类型 json格式则为application/json
        mq_input:
          binder: rabbit-demo
          destination: mq-dest
          content-type: text/plain   #消息接收类型
          group: mq-cus   #消息分组 防止多个实例下的重复消费
        kafka_output:
          binder: kafka-demo
          destination: kafka-dest
          content_type: text/plain
        kafka_input:
          binder: kafka-demo
          destination: kafka-dest
          content_type: text/plain
          group: kafka-cus
          listener:
            concurrency: 3 #根据分区决定消费线程数
          consumer:
            group-id: kafka-consumer
            enable-auto-commit: true
            auto-offset-reset: earliest #从提交的offset开始消费
            max-poll-records: 1000
      binders:
        kafka-demo:   #kafka配置
          type: kafka
          environment:
            spring:
              cloud:
                stream:
                  kafka:
                    binder:
                      brokers: ip:port #集群以，分开
                      auto-add-partitions: true   #根据需要自动创建新分区
                      auto-create-topics: true    #自动创建主题 若为false且主题不存在 binder无法启动
                      min-partition-count: 1      #仅在设置autoCreateTopics或autoAddPartitions时有效 全局分区的最小个数
        rabbit-demo:  #rabbit配置
          type: rabbit
          environment:
            spring:
              rabbitmq:
                addresses: ip
                port: 5672
                username: admin
                password: admin


```
binders为不同消息组件的配置，在自定义消息通道时可以指定配置的消息binder,具体说明标注于yml文件上。

### 自定义消息通道

springcloudstream有默认通道（input,out）,而配置文件中的mq_output、mq_input、kafka_output、kafka_input为代码中自定义的消息通道
``` java
/**
 * @Author: silly-billy
 * @Date: 2020/3/31 11:04
 * @Version 1.0
 */
public interface KafkaChannel {

    //kafka消息通道
    String KAFKA_OUT_PUT = "kafka_output";

    String KAFKA_IN_PUT = "kafka_input";

    @Output(KAFKA_OUT_PUT)
    MessageChannel kafkaOutput();
    @Input(KAFKA_IN_PUT)
    SubscribableChannel kafkaInput();

}
```
``` java
/**
 * @Author: silly-billy
 * @Date: 2020/3/31 10:53
 * @Version 1.0
 */
public interface MqChannel {
    //rabbitmq 消息生产者通道
    String MQ_OUT_PUT = "mq_output";
    //消费者通道
    String MQ_IN_PUT = "mq_input";

    @Output(MQ_OUT_PUT)
    MessageChannel OutPut();

    @Input(MQ_IN_PUT)
    SubscribableChannel InPut();
}
```

### 编写消息的生产者
``` java
/**
 * @Author: silly-billy
 * @Date: 2020/3/31 11:53
 * @Version 1.0
 */
@EnableBinding(KafkaChannel.class)
public class KafkaMsgProducer {

    @Autowired
    private KafkaChannel source;

    /**
     * 发送一个Message到此频道。如果消息发送成功，则该方法返回true。
     * 如果由于非致命原因而无法发送消息，则该方法返回false。
     * 如果发生不可恢复的错误，该方法还可能引发RuntimeException
     * @param msg
     * @return
     */
    public boolean sendMsg(String msg){
        return source.kafkaOutput().send(MessageBuilder.withPayload(msg).setHeader("flag","test").build());
    }

}
```
``` java
/**
 * @Author: silly-billy
 * @Date: 2020/3/31 11:08
 * @Version 1.0
 */
@Slf4j
@EnableBinding(MqChannel.class)
public class MqMsgProducer {

    @Autowired
    @Output(MqChannel.MQ_OUT_PUT)
    private MessageChannel channel;


    public boolean sendMsg(String msg) {
        return channel.send(MessageBuilder.withPayload(msg).build());
    }

}
```
### 编写消息的消费者

``` java
/**
 * @Author: silly-billy
 * @Date: 2020/3/31 11:54
 * @Version 1.0
 */
@Slf4j
@EnableBinding(KafkaChannel.class)
public class KafkaMsgConsumer {

    @StreamListener(value = KafkaChannel.KAFKA_IN_PUT,condition = "headers['flag']=='test'")
    public void recieve0(Object payload){
        log.info("kafka receive {}",payload);
    }
}
```
``` java
/**
 * @Author: silly-billy
 * @Date: 2020/3/31 11:15
 * @Version 1.0
 */
@Slf4j
@EnableBinding(MqChannel.class)
public class MqMsgConsumer {

    @StreamListener(MqChannel.MQ_IN_PUT)
    public void messageInPut(Message<String> message) {
        log.info("rabbit receive：{}",message.getPayload());
    }
}
```
到这里demo就已经完成了springcloudstream对kafka和rabbitmq消息收发的处理。整个工程只需要简单配置消息组件的可选项，并且完全屏蔽了它的底层实现，方便迁移。

## 测试

为了验证程序的正确性，编写controller层相关代码，然后用postman进行测试。

``` java
/**
 * @Author: silly-billy
 * @Date: 2020/3/31 11:17
 * @Version 1.0
 */
@Controller
public class KafkaController {

    @Autowired
    private KafkaMsgProducer kafkaMsgProducer;

    @GetMapping(value = "/testKafka")
    @ResponseBody
    public boolean testKafka(@RequestParam("msg")String msg){
        return kafkaMsgProducer.sendMsg(msg);
    }
}
```
``` java
/**
 * @Author: silly-billy
 * @Date: 2020/3/31 11:16
 * @Version 1.0
 */
@Controller
public class MqController {

    @Autowired
    private MqMsgProducer mqMessageProducer;

    @GetMapping(value = "/testMq")
    @ResponseBody
    public boolean testMq(@RequestParam("msg")String msg){
        return mqMessageProducer.sendMsg(msg);
    }
}
```
### 调试结果
