#### 已整理到 ``README.md``

##### NameServer 
- NamesrvStartup # start 【 NameServer启动 】

##### 消息存储~CommitLog 
    * MessageStoreConfig (存储相关的配置，例如存储路径、commitLog文件大小，刷盘频次)

- DefaultMessageStore # putMessage 【 消息存储流程 】 
    - commitLog # putMessage 【将日志写入 commitLog 】 
        - 消息写入文件逻辑 mappedFile.appendMessage
            - 具体写入逻辑，CommitLog # doAppend  
        - 文件刷盘 handleDiskFlush
            -  具体刷盘代码。 GroupCommitService # doCommit 
        - 主从同步 handleHA


##### 消息生产~Producer
- DefaultMQProducerImpl # sendDefaultImpl 【消息发送】
    - tryToFindTopicPublishInfo 【寻找主题路由】
    - selectOneMessageQueue 【选择 MessageQueue】（实现类在 MQFaultStrategy）
    - `sendKernelImpl 核心发送消息` 【根据MessageQueue 向特定Broker 发送消息】
    - updateFaultItem 【更新延迟容错信息】
        - LatencyFaultToleranceImpl 延迟故障容错实现类
    
##### 消息消费~Consumer
- DefaultMQPushConsumerImpl # start 【消息消费】
###### 消息消费处理
- ConsumeMessageConcurrentlyService # run()
    - listener.consumeMessage() 【 消息消费状态】
    - processConsumeResult()  【 消息消费结果处理】
##### 延迟消息机制
- CommitLog # putMessage()
> 在消息存入commitlog之前，如果发现延迟level大于0，会将消息的主题设置为SCHEDULE_TOPIC = "SCHEDULE_TOPIC_XXXX"，然后备份原主题名称。那就清晰明了，延迟消息统一由 ScheduleMessageService 来处理