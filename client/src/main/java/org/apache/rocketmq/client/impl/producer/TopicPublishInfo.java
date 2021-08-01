/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.client.impl.producer;

import java.util.ArrayList;
import java.util.List;
import org.apache.rocketmq.client.common.ThreadLocalIndex;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.route.QueueData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;

public class TopicPublishInfo {
    private boolean orderTopic = false;
    private boolean haveTopicRouterInfo = false;
    private List<MessageQueue> messageQueueList = new ArrayList<MessageQueue>();
    private volatile ThreadLocalIndex sendWhichQueue = new ThreadLocalIndex();
    private TopicRouteData topicRouteData;

    public boolean isOrderTopic() {
        return orderTopic;
    }

    public void setOrderTopic(boolean orderTopic) {
        this.orderTopic = orderTopic;
    }

    public boolean ok() {
        return null != this.messageQueueList && !this.messageQueueList.isEmpty();
    }

    public List<MessageQueue> getMessageQueueList() {
        return messageQueueList;
    }

    public void setMessageQueueList(List<MessageQueue> messageQueueList) {
        this.messageQueueList = messageQueueList;
    }

    public ThreadLocalIndex getSendWhichQueue() {
        return sendWhichQueue;
    }

    public void setSendWhichQueue(ThreadLocalIndex sendWhichQueue) {
        this.sendWhichQueue = sendWhichQueue;
    }

    public boolean isHaveTopicRouterInfo() {
        return haveTopicRouterInfo;
    }

    public void setHaveTopicRouterInfo(boolean haveTopicRouterInfo) {
        this.haveTopicRouterInfo = haveTopicRouterInfo;
    }

    /**
     * 随机，选择一个 Broker。
     * @param lastBrokerName
     * @return
     */
    public MessageQueue selectOneMessageQueue(final String lastBrokerName) {
        // 如果没有 上一次使用的 Broker 作为参考，那么随机选择一个 Broker
        if (lastBrokerName == null) {
            return selectOneMessageQueue();
        } else {

            // 如果存在上一次使用 Broker。就选择非上一次使用的 Broker。
            // 目的：均匀分散Broker 压力。
            int index = this.sendWhichQueue.getAndIncrement();
            for (int i = 0; i < this.messageQueueList.size(); i++) {
                int pos = Math.abs(index++) % this.messageQueueList.size();
                if (pos < 0)
                    pos = 0;
                MessageQueue mq = this.messageQueueList.get(pos);
                if (!mq.getBrokerName().equals(lastBrokerName)) {
                    return mq;
                }
            }

            // 以上都没有选择。采用兜底方案。随机选择一个 Broker。
            return selectOneMessageQueue();
        }
    }

    public MessageQueue selectOneMessageQueue() {
        int index = this.sendWhichQueue.getAndIncrement();
        int pos = Math.abs(index) % this.messageQueueList.size();
        if (pos < 0)
            pos = 0;
        return this.messageQueueList.get(pos);
    }

    public int getQueueIdByBroker(final String brokerName) {
        for (int i = 0; i < topicRouteData.getQueueDatas().size(); i++) {
            final QueueData queueData = this.topicRouteData.getQueueDatas().get(i);
            if (queueData.getBrokerName().equals(brokerName)) {
                return queueData.getWriteQueueNums();
            }
        }

        return -1;
    }

    @Override
    public String toString() {
        return "TopicPublishInfo [orderTopic=" + orderTopic + ", messageQueueList=" + messageQueueList
            + ", sendWhichQueue=" + sendWhichQueue + ", haveTopicRouterInfo=" + haveTopicRouterInfo + "]";
    }

    public TopicRouteData getTopicRouteData() {
        return topicRouteData;
    }

    public void setTopicRouteData(final TopicRouteData topicRouteData) {
        this.topicRouteData = topicRouteData;
    }
}
