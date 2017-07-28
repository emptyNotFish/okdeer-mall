
package com.okdeer.mall.order.pay.callback;

import org.springframework.stereotype.Service;

import com.okdeer.mall.order.entity.TradeOrder;
import com.okdeer.mall.order.enums.OrderStatusEnum;

/**
 * ClassName: ScanOrderPayHandler 
 * @Description: 扫码够订单支付成功后处理
 * @author zengjizu
 * @date 2017年3月24日
 *
 * =================================================================================================
 *     Task ID			  Date			     Author		      Description
 * ----------------+----------------+-------------------+-------------------------------------------
 *    V2.2.0              2017-3-24          zengjz           扫码够订单支付成功后处理
 */
@Service("scanOrderPayHandler")
public class ScanOrderPayHandler extends AbstractPayResultHandler {
	
	
	@Override
	protected void setOrderStatus(TradeOrder tradeOrder) {
		tradeOrder.setStatus(OrderStatusEnum.HAS_BEEN_SIGNED);
	}
	
	@Override
	public void postProcessOrder(TradeOrder tradeOrder) throws Exception {
		
	}

	@Override
	protected void sendNotifyMessage(TradeOrder tradeOrder) throws Exception {
		
	}
	
	@Override
	public void sendTimerMessage(TradeOrder tradeOrder) throws Exception {
		
	}
	
	@Override
	public void preProcessOrder(TradeOrder tradeOrder) throws Exception {
		
	}
	@Override
	protected void processOrderItem(TradeOrder tradeOrder) throws Exception {
		
	}
}
