package com.okdeer.mall.order.pay.callback;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.okdeer.mall.order.entity.TradeOrder;
import com.okdeer.mall.order.enums.OrderResourceEnum;
import com.okdeer.mall.order.enums.OrderTypeEnum;

@Service
public class PayResultHandlerFactory {

	@Autowired
	private AbstractPayResultHandler physicalOrderPayHandler;
	
	@Autowired
	private AbstractPayResultHandler serviceOrderPayHandler;
	
	@Autowired
	private AbstractPayResultHandler storeConsumOrderPayHandler;
	
	@Autowired
	private AbstractPayResultHandler phoneOrderPayHandler;
	
	@Autowired
	private AbstractPayResultHandler trafficOrderPayHandler;
	
	//start add by zengjz 2017-3-24 增加扫码购支付成功后处理
	@Autowired
	private ScanOrderPayHandler scanOrderPayHandler;
	//end add by zengjz 2017-3-24 增加扫码购支付成功后处理
	
	public AbstractPayResultHandler getByOrderType(OrderTypeEnum orderType){
		AbstractPayResultHandler handler = null;
		switch (orderType) {
			case PHYSICAL_ORDER:
				handler = physicalOrderPayHandler;
				break;
			case SERVICE_STORE_ORDER:
				handler = serviceOrderPayHandler;			
				break;
			case PHONE_PAY_ORDER:
				handler = phoneOrderPayHandler;
				break;
			case TRAFFIC_PAY_ORDER:
				handler = trafficOrderPayHandler;	
				break;
			case STORE_CONSUME_ORDER:
				handler = storeConsumOrderPayHandler;
				break;
			default:
				break;
		}
		return handler;
	}
	
	/**
	 * @Description: 根据订单信息获取处理类
	 * @param tradeOrder 订单信息
	 * @return
	 * @author zengjizu
	 * @date 2017年3月24日
	 */
	public AbstractPayResultHandler getByOrder(TradeOrder tradeOrder){
		AbstractPayResultHandler handler = null;
		if( OrderTypeEnum.PHYSICAL_ORDER == tradeOrder.getType() && OrderResourceEnum.SWEEP == tradeOrder.getOrderResource()){
			//如果是扫码够订单使用扫码够处理handler
			handler = scanOrderPayHandler;
		}else{
			//根据订单类型来获取handler
			handler = getByOrderType(tradeOrder.getType());
		}
		return handler;
	}
}
