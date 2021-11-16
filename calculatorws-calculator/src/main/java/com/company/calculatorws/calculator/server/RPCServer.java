package com.company.calculatorws.calculator.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.company.calculatorws.calculator.handler.MessageHandler;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

@Component
public class RPCServer {
	//定义连接工厂
	private ConnectionFactory factory = new ConnectionFactory();
	
	private boolean stop = false;
	
	@Autowired
	private MessageHandler messageHandler;
	
	private Connection connection = null;

	public void start() {
		connection = null;
		Channel channel = null;
		try {
			//设置服务器地址
			factory.setHost("localhost");
			//使用默认端口
			// factory.setPort(5672);
			// 设置账号信息，用户名、密码、virtualhost
			// factory.setVirtualHost("/");//设置虚拟机，一个mq服务可以设置多个虚拟机，每个虚拟机就相当于一个独立的mq
			// factory.setUsername("");
			// factory.setPassword("");
			// 通过工厂获取连接
			connection = factory.newConnection();
			//建立通信channel
			channel = connection.createChannel();
			// 声明（创建）队列
			//参数：String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments
			/**
			 * 参数明细
			 * 1、queue 队列名称
			 * 2、durable 是否持久化，如果持久化，mq重启后队列还在
			 * 3、exclusive 是否独占连接，队列只允许在该连接中访问，如果connection连接关闭队列则自动删除,如果将此参数设置true可用于临时队列的创建
			 * 4、autoDelete 自动删除，队列不再使用时是否自动删除此队列，如果将此参数和exclusive参数设置为true就可以实现临时队列（队列不用了就自动删除）
			 * 5、arguments 参数，可以设置一个队列的扩展参数，比如：可设置存活时间
			 */
			channel.queueDeclare(RPC_QUEUE_NAME, false, false, false, null);
			channel.basicQos(1);

			QueueingConsumer consumer = new QueueingConsumer(channel);
			// 监听队列，第二个参数：是否自动进行消息确认。
			//参数：String queue, boolean autoAck, Consumer callback
			/**
			 * 参数明细：
			 * 1、queue 队列名称
			 * 2、autoAck 自动回复，当消费者接收到消息后要告诉mq消息已接收，如果将此参数设置为tru表示会自动回复mq，如果设置为false要通过编程实现回复
			 * 3、callback，消费方法，当消费者接收到消息要执行的方法
			 */
			channel.basicConsume(RPC_QUEUE_NAME, false, consumer);

			logger.info(">>>>> Waiting for requests <<<<<");

			while (!stop) {
				String response = null;
				
				QueueingConsumer.Delivery delivery = consumer.nextDelivery();
				BasicProperties props = delivery.getProperties();
				String corrId = props.getCorrelationId();
				BasicProperties replyProps = new BasicProperties.Builder().correlationId(corrId).build();
				
				try {
					String message = new String(delivery.getBody(),"UTF-8");
					logger.info(String.format("<-- Got from %s : %s", corrId, message));
					
					response = messageHandler.process(message);
					
					logger.info(String.format("--> Sending to %s : %s", corrId, response));
				}
				catch (Exception e){
					response = String.format("ERROR: %s", e.getMessage());
					logger.info(String.format("--> Sending to %s : %s", corrId, response));
				}
				finally {  
					channel.basicPublish( "", props.getReplyTo(), replyProps, response.getBytes("UTF-8"));
					channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
				}
			}
		}
		catch  (Exception e) {
			logger.error("An error has occurred!", e);
		}
		finally {
			stop();
		}
    }
	
	public void stop() {
		stop = true;
		
		if (connection != null) {
			try {
				connection.close();
			}
			catch (Exception ignore) {}
		}
	}
	
	private static final String RPC_QUEUE_NAME = "rpc_queue";
	
	private static final Logger logger = LoggerFactory.getLogger(RPCServer.class);
}
