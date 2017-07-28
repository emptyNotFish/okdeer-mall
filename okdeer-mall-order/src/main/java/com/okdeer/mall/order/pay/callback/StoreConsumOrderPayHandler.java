package com.okdeer.mall.order.pay.callback;

import java.io.Serializable;
import java.util.Date;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.okdeer.base.common.utils.mapper.JsonMapper;
import com.okdeer.base.framework.mq.RocketMQProducer;
import com.okdeer.base.framework.mq.message.MQMessage;
import com.okdeer.mall.order.entity.TradeOrder;
import com.okdeer.mall.order.enums.ConsumerCodeStatusEnum;
import com.okdeer.mall.order.mq.constants.TradeOrderTopic;
import com.okdeer.mall.order.service.TradeOrderService;

@Service("storeConsumOrderPayHandler")
public class StoreConsumOrderPayHandler extends AbstractPayResultHandler{
	
	@Resource
	private TradeOrderService tradeOrderService;
	/**
	 * MQ信息
	 */
	@Autowired
	RocketMQProducer rocketMQProducer;
	
	@Override
	public void preProcessOrder(TradeOrder tradeOrder) throws Exception{
		tradeOrder.setUpdateTime(new Date());
		tradeOrder.setConsumerCodeStatus(ConsumerCodeStatusEnum.WAIT_CONSUME);
		// 增加回款时间
		tradeOrder.setPaymentTime(new Date());
	}
	
	@Override
	public void processOrderItem(TradeOrder tradeOrder) throws Exception{
		tradeOrderService.dealWithStoreConsumeOrder(tradeOrder);
	}
	
	@Override
	protected void sendNotifyMessage(TradeOrder tradeOrder) throws Exception {
		// 店消费的处理全部在processOrderItem中完成
		MQMessage anMessage = new MQMessage(TradeOrderTopic.ORDER_COMPLETE_TOCPIC, (Serializable) tradeOrder);
		try {
			rocketMQProducer.sendMessage(anMessage);
		} catch (Exception e) {
			logger.error("完成订单发送消息异常{}",JsonMapper.nonEmptyMapper().toJson(tradeOrder), e);
		}
	}
}
