#### mq4.6_addlog baranch

##### NameServer 
- NamesrvStartup # start 【 NameServer启动 】

##### 消息存储 
    * MessageStoreConfig (存储相关的配置，例如存储路径、commitLog文件大小，刷盘频次)

- DefaultMessageStore # putMessage 【 消息存储流程 】 
    - commitLog # putMessage 【将日志写入 commitLog 】 
        - 消息写入文件逻辑 mappedFile.appendMessage
            - 具体写入逻辑，CommitLog # doAppend  
        - 文件刷盘 handleDiskFlush
            -  具体刷盘代码。 GroupCommitService # doCommit 
        - 主从同步 handleHA


##### 消息 producer
- DefaultMQProducerImpl # sendDefaultImpl 【消息发送】
    - tryToFindTopicPublishInfo 【寻找主题路由】
    - selectOneMessageQueue 【选择 MessageQueue】
    - sendKernelImpl 【根据MessageQueue 向特定Broker 发送消息】
    
