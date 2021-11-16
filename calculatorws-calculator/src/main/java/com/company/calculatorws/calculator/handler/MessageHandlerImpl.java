package com.company.calculatorws.calculator.handler;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.company.calculatorws.calculator.exception.CalculatorException;
import com.company.calculatorws.calculator.operation.Operation;

@Component
public class MessageHandlerImpl implements MessageHandler {

	private static String operPackage = "com.company.calculatorws.calculator.operation";

	private static final Logger logger = LoggerFactory.getLogger(MessageHandlerImpl.class);

	public MessageHandlerImpl() {}

	@Override
	public String process(String message) {
		String result = null;
		
		try { 
			String[] arrMessage = message.split(",");		
			//利用多态性的思想，将具体的操作交给操作接口，引用接口的方法调用实例的方法
			//操作的获取是反射的思想
			Operation oper = createObject(arrMessage[0]);
			BigDecimal a = new BigDecimal(arrMessage[1]);
			BigDecimal b = new BigDecimal(arrMessage[2]);
			//返回BigDecimal的String
			result = oper.calculate(a, b).toString();
		}
		catch (ArrayIndexOutOfBoundsException e) {
			String exceptionMessage = String.format("Message is malformatted! '%s'", message);
			logger.error(exceptionMessage);
			throw new CalculatorException(exceptionMessage + " Usage: 'OPER,A,B'", e);
		}
		catch (ClassNotFoundException e) {
			String exceptionMessage = String.format("Operation is unknown! '%s'", message);
			logger.error(exceptionMessage);
			throw new CalculatorException(exceptionMessage, e);
		}
		catch (InstantiationException e) {
			String exceptionMessage = String.format("Unable to instantiate operation!", e.getMessage());
			logger.error(exceptionMessage);
			throw new CalculatorException(exceptionMessage, e);
		}
		catch (IllegalAccessException e) {
			logger.error(e.getMessage());
			throw new CalculatorException(e);
		}
		
		return result;
	}	

	private static Operation createObject(String className) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		Class<?> clazz = Class.forName(operPackage + "." + className);
		return (Operation) clazz.newInstance();
	}


}
