package com.okdeer.mall.order.pay.callback;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.okdeer.mall.common.utils.RandomStringUtil;
import com.okdeer.mall.order.bo.TradeOrderContext;
import com.okdeer.mall.order.entity.TradeOrder;
import com.okdeer.mall.order.enums.PickUpTypeEnum;
import com.okdeer.mall.order.service.TradeorderProcessLister;
import com.okdeer.mall.order.timer.TradeOrderTimer;

@Service("physicalOrderPayHandler")
public class PhysicalOrderPayHandler extends AbstractPayResultHandler {
	
	@Autowired
	@Qualifier(value="jxcSynTradeorderProcessLister")
	private TradeorderProcessLister tradeorderProcessLister;
	
	@Override
	public void preProcessOrder(TradeOrder tradeOrder) throws Exception{
		// 实物订单，如果是到店自提，需要生成提货码
		if (tradeOrder.getPickUpType() == PickUpTypeEnum.TO_STORE_PICKUP) {
			tradeOrder.setPickUpCode(RandomStringUtil.getRandomInt(6));
		}
	}
	
	@Override
	public void sendTimerMessage(TradeOrder tradeOrder) throws Exception {
		// 发送计时消息
		
		if (tradeOrder.getPickUpType() == PickUpTypeEnum.TO_STORE_PICKUP){ 
			tradeOrderTimer.sendTimerMessage(TradeOrderTimer.Tag.tag_take_goods_timeout, tradeOrder.getId());
		} else {
			tradeOrderTimer.sendTimerMessage(TradeOrderTimer.Tag.tag_delivery_timeout, tradeOrder.getId());
		}
		
	}
	
	@Override
	public void postProcessOrder(TradeOrder tradeOrder) throws Exception {
		//支付完成
		TradeOrderContext tradeOrderContext = new TradeOrderContext();
		tradeOrderContext.setTradeOrder(tradeOrder);
		tradeOrderContext.setTradeOrderPay(tradeOrder.getTradeOrderPay());
		tradeOrderContext.setItemList(tradeOrder.getTradeOrderItem());
		tradeOrderContext.setTradeOrderLogistics(tradeOrder.getTradeOrderLogistics());
		tradeorderProcessLister.tradeOrderStatusChange(tradeOrderContext);
	}

}
