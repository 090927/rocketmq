#### NameServer 
- NamesrvStartup # start 【 NameServer启动 】




##### 消息生产~Producer
- **DefaultMQProducerImpl # sendDefaultImpl** 【消息发送】
    - ``tryToFindTopicPublishInfo`` 【寻找主题路由】
    - ``selectOneMessageQueue`` 【根据topic 选择 MessageQueue】（实现类在 MQFaultStrategy）
    - `sendKernelImpl 核心发送消息` 【根据MessageQueue 向特定Broker 发送消息】
    - ``updateFaultItem`` [ 更新延迟容错信息 ]
        - LatencyFaultToleranceImpl [ 延迟故障容错实现类 ]



##### Broker
###### Broker 接收消息（ Producer 生产消息后）
- SendMessageProcessor # processRequest 【 Broker 接收消息】
    - AbstractSendMessageProcessor # msgCheck [ 消息校验]     
        - ``DefaultMessageStore # putMessage`` [ 消息存储 ]
            - CommitLong # putMessage [消息存储 CommitLog ]


``Broker -> MessageStore -> CommitLog -> MappedFileQueue -> MappedFile -> File``
- 即MappedFile中第一个消息全局物理偏移量，也是MappedFile的文件名


#### 消息存储~CommitLog 
    * MessageStoreConfig (存储相关的配置，例如存储路径、commitLog文件大小，刷盘频次)
- DefaultMessageStore # putMessage 【 消息存储流程 】 
    - commitLog # putMessage 【将日志写入 commitLog 】 
        - 消息写入文件逻辑 ``mappedFile # appendMessage``
            - 具体写入逻辑，``CommitLog # doAppend``  
        - 文件刷盘 ``handleDiskFlush``
            -  【 同步刷盘 】GroupCommitService # doCommit 
            -  【 异步刷盘 && 开启内存字节缓冲区】CommitRealTimeService
            -  【 异步刷盘 && 关闭内存字节缓冲区 】FlushRealTimeService              
        - 主从同步 ``handleHA`` 
    
    
##### 消息消费~Consumer
- DefaultMQPushConsumerImpl # start 【消息消费】


###### `ConsumeQueue`
- ReputMessageService ``write ConsumeQueue`` [ 就是一个线程用来更新ConsumeQueue中消息偏移的 ]
    - 【 核心 doDispatch 方法 】
    - DefaultMessageStore # putMessagePositionInfo ``建立 消息位置信息 到 ConsumeQueue``
        - ``putMessagePositionInfoWrapper`` 核心方法
            - ``putMessagePositionInfo`` 添加位置信息，并返回添加是否成功。

- FlushConsumeQueueService ``Flush ConsumerQueue``


###### `Broker 提供[ 拉取消息 ]接口`
- PullMessageProcessor # processRequest [ 拉取消 ]
    - DefaultMessageStore # getMessage [ 获取消息结果 ] 

###### PushConsumer 订阅
- DefaultMQPushConsumerImpl # subscribe

- ``RebalanceService`` 均衡消息队列服务
    - ``RebalanceImpl # doRebalance``  [ 执行分配消息队列 ]      

   
###### 消息消费处理
- ConsumeMessageConcurrentlyService # run()
    - listener.consumeMessage() 【 消息消费状态】
    - processConsumeResult()  【 消息消费结果处理】

##### 延迟消息机制
- CommitLog # putMessage() ``tranType == MessageSysFlag.TRANSACTION_NOT_TYPE 延迟消息处理``
- DeliverDelayedMessageTimerTask ``发送延时消息定时任务``
- ScheduleMessageService ``Broker 持久化定时发送进度 ``

> 在消息存入commitlog之前，如果发现延迟level大于0，会将消息的主题设置为SCHEDULE_TOPIC = "SCHEDULE_TOPIC_XXXX"，
>然后备份原主题名称。那就清晰明了，延迟消息统一由 ScheduleMessageService 来处理