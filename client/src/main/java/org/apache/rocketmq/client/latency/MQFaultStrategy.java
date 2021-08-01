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

package org.apache.rocketmq.client.latency;

import org.apache.rocketmq.client.impl.producer.TopicPublishInfo;
import org.apache.rocketmq.client.log.ClientLogger;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.common.message.MessageQueue;

public class MQFaultStrategy {
    private final static InternalLogger log = ClientLogger.getLog();

    /**
     * 延迟故障容错，维护每个Broker的发送消息的延迟
     */
    private final LatencyFaultTolerance<String> latencyFaultTolerance = new LatencyFaultToleranceImpl();

    /**
     * 发送消息延迟容错开关
     */
    private boolean sendLatencyFaultEnable = false;
    // 最大延迟时间数值，在消息发送之前，先纪律当前时间 start。然后在发送成功或失败时记录当前时间 end（end-start)代表一次消费延迟时间。
    private long[] latencyMax = {50L, 100L, 550L, 1000L, 2000L, 3000L, 15000L};
    private long[] notAvailableDuration = {0L, 0L, 30000L, 60000L, 120000L, 180000L, 600000L};

    public long[] getNotAvailableDuration() {
        return notAvailableDuration;
    }

    public void setNotAvailableDuration(final long[] notAvailableDuration) {
        this.notAvailableDuration = notAvailableDuration;
    }

    public long[] getLatencyMax() {
        return latencyMax;
    }

    public void setLatencyMax(final long[] latencyMax) {
        this.latencyMax = latencyMax;
    }

    public boolean isSendLatencyFaultEnable() {
        return sendLatencyFaultEnable;
    }

    public void setSendLatencyFaultEnable(final boolean sendLatencyFaultEnable) {
        this.sendLatencyFaultEnable = sendLatencyFaultEnable;
    }

    /**
     * 获取发送者 messageQueue。
     * @param tpInfo
     * @param lastBrokerName
     * @return
     */
    public MessageQueue selectOneMessageQueue(final TopicPublishInfo tpInfo, final String lastBrokerName) {
        /**
         * "sendLatencyFaultEnable"
         * 是否开启消息延迟规避机制，是消息发送者哪里可以设置。
         * 如果为 false，直接从 topic 的所有队列中选择下一个。而不考虑该队列是否可用。
         */
        if (this.sendLatencyFaultEnable) {
            try {
                /**
                 *  【 第一步 】 获取一个自增序号 `index` 通过取模获取 `Queue` 的位置下标 Pos.
                 *      如果 Pos 对应的Broker 的延迟时间是可以接收的，并且是第一次发送。或者和上次发送 Broker 相同。则将 Queue 返回。
                 *
                 * 使用本地线程变量，ThreadLocal 保存上一次发送的队列下标，消息发送使用轮训机制获取下一个发送消息队里。
                 * 循环，因为加入发送异常延迟，要确保选中的消息队列（MessageQueue）所在的Broker 是否正常
                 */
                int index = tpInfo.getSendWhichQueue().getAndIncrement();
                for (int i = 0; i < tpInfo.getMessageQueueList().size(); i++) {
                    int pos = Math.abs(index++) % tpInfo.getMessageQueueList().size();
                    if (pos < 0)
                        pos = 0;
                    MessageQueue mq = tpInfo.getMessageQueueList().get(pos);

                    // 判断当前的消息队列是否可用。
                    if (latencyFaultTolerance.isAvailable(mq.getBrokerName())) {
                        if (null == lastBrokerName || mq.getBrokerName().equals(lastBrokerName))
                            return mq;
                    }
                }

                // 【 第二步 】如果第一步，没有选中一个 Broker,则选择一个延迟较低的 Broker
                // [ pickOneAtLeast ] 选择优秀对象，类似延迟队列
                final String notBestBroker = latencyFaultTolerance.pickOneAtLeast();
                // 根据broker 的 startTimestart 进行一个排序，值越小，排前面，然后再选择一个，
                int writeQueueNums = tpInfo.getQueueIdByBroker(notBestBroker);
                if (writeQueueNums > 0) {
                    final MessageQueue mq = tpInfo.selectOneMessageQueue();
                    if (notBestBroker != null) {
                        mq.setBrokerName(notBestBroker);
                        mq.setQueueId(tpInfo.getSendWhichQueue().getAndIncrement() % writeQueueNums);
                    }
                    return mq;
                } else {
                    latencyFaultTolerance.remove(notBestBroker);
                }
            } catch (Exception e) {
                log.error("Error occurred when selecting message queue", e);
            }

            // 选择一个消息队列，不考虑队列的可用性
            return tpInfo.selectOneMessageQueue();
        }

        /**
         * 获得 lastBrokerName 对应的一个消息队列，不考虑该队列的可用性
         *
         *   [第三步] {@link TopicPublishInfo#selectOneMessageQueue(String)}
         *      随机选择一个 Broker。
         */
        return tpInfo.selectOneMessageQueue(lastBrokerName);
    }

    /**
     * 更新延迟容错信息
     *
     * @param brokerName brokerName
     * @param currentLatency 延迟
     * @param isolation 是否隔离。当开启隔离时，默认延迟为30000。目前主要用于发送消息异常时
     */
    public void updateFaultItem(final String brokerName, final long currentLatency, boolean isolation) {
        if (this.sendLatencyFaultEnable) {
            // 计算延迟对应的不可用时间
            long duration = computeNotAvailableDuration(isolation ? 30000 : currentLatency);
            // [ updateFaultItem ]
            this.latencyFaultTolerance.updateFaultItem(brokerName, currentLatency, duration);
        }
    }

    /**
     * 计算延迟对应的不可用时间
     * @param currentLatency 延迟
     * @return 不可以时间
     */
    private long computeNotAvailableDuration(final long currentLatency) {
        for (int i = latencyMax.length - 1; i >= 0; i--) {
            if (currentLatency >= latencyMax[i])
                return this.notAvailableDuration[i];
        }

        return 0;
    }
}
