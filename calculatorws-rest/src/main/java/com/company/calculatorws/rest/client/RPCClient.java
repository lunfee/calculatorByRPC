package com.company.calculatorws.rest.client;

import java.util.UUID;

import org.apache.log4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

@Component
public class RPCClient {
	//连接工厂
	private static ConnectionFactory factory = new ConnectionFactory();	
	
	private Connection connection;
	private Channel channel;
	private String requestQueueName = "rpc_queue";
	private String replyQueueName;
	private QueueingConsumer consumer;

	public RPCClient() throws Exception {
		factory.setHost("121.89.198.164");
		connection = factory.newConnection();
		channel = connection.createChannel();



		//客户端创建匿名回调队列 Callback Queue
		replyQueueName = channel.queueDeclare().getQueue();
		//创建消费者
		consumer = new QueueingConsumer(channel);
		// 监听队列，第二个参数：是否自动进行消息确认。
		//参数：String queue, boolean autoAck, Consumer callback
		/**
		 * 参数明细：
		 * 1、queue 队列名称
		 * 2、autoAck 自动回复，当消费者接收到消息后要告诉mq消息已接收，如果将此参数设置为tru表示会自动回复mq，如果设置为false要通过编程实现回复
		 * 3、callback，消费方法，当消费者接收到消息要执行的方法
		 */
		channel.basicConsume(replyQueueName, true, consumer);
	}

	public String call(String message) throws Exception {
		String response = null;
		//客户端为RPC请求设置2个属性：replyTo，设置回调队列名字；correlationId，标记request。
		String corrId = (String) MDC.get("UNIQUE_ID");
		if ( corrId == null )
			corrId = UUID.randomUUID().toString();
		
		logger.info(String.format("%s --> Requesting : %s", corrId, message));
		//Message properties,对request进行标记
		BasicProperties props = new BasicProperties
				.Builder().correlationId(corrId).replyTo(replyQueueName).build();

		// 向指定的队列中发送消息
		//参数：String exchange, String routingKey, BasicProperties props, byte[] body
		/**
		 * 参数明细：
		 * 1、exchange，交换机，如果不指定将使用mq的默认交换机（设置为""）
		 * 2、routingKey，路由key，交换机根据路由key来将消息转发到指定的队列，如果使用默认交换机，routingKey设置为队列的名称
		 * 3、props，消息的属性
		 * 4、body，消息内容
		 */
		//请求被发送到rpc_queue队列中。
		channel.basicPublish("", requestQueueName, props, message.getBytes("UTF-8"));

		while (true) {
			QueueingConsumer.Delivery delivery = consumer.nextDelivery();
			if (delivery.getProperties().getCorrelationId().equals(corrId)) {
				response = new String(delivery.getBody(),"UTF-8");
				break;
			}
		}
		
		logger.info(String.format("%s <-- Got : %s", corrId, response));

		return response;
	}

	public void close() throws Exception {
		connection.close();
	}
	
	private static final Logger logger = LoggerFactory.getLogger(RPCClient.class);
}
