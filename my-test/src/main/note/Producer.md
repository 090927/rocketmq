#### 生产者启动

- `DefaultMQProducer # start()`


##### 消息生产~Producer
- `DefaultMQProducer # send()`
- **DefaultMQProducerImpl # sendDefaultImpl** 【消息发送】
    - ``tryToFindTopicPublishInfo`` 【寻找主题路由】
    - ``selectOneMessageQueue`` 【根据topic 选择 MessageQueue】（实现类在 MQFaultStrategy）
    - `sendKernelImpl 核心发送消息` 【根据MessageQueue 向特定Broker 发送消息】
    - ``updateFaultItem`` [ 更新延迟容错信息 ]
        - LatencyFaultToleranceImpl [ 延迟故障容错实现类 ]