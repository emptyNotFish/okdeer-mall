/**   
* @Title: AlipayStatusSubscriber.java 
* @Package com.okdeer.mall.trade.order.pay 
* @Description: (用一句话描述该文件做什么) 
* @author A18ccms A18ccms_gmail_com   
* @date 2016年3月30日 下午7:39:54 
* @version V1.0   
*/
package com.okdeer.mall.order.pay;

import java.util.List;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import com.alibaba.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import com.alibaba.rocketmq.common.message.MessageExt;
import com.google.common.base.Charsets;
import com.okdeer.api.pay.pay.dto.PayResponseDto;
import com.okdeer.base.common.utils.StringUtils;
import com.okdeer.base.common.utils.mapper.JsonMapper;
import com.okdeer.base.framework.mq.AbstractRocketMQSubscriber;
import com.okdeer.mall.order.constant.mq.OrderMessageConstant;
import com.okdeer.mall.order.constant.mq.PayMessageConstant;
import com.okdeer.mall.order.constant.text.ExceptionConstant;
import com.okdeer.mall.order.entity.TradeOrder;
import com.okdeer.mall.order.enums.OrderResourceEnum;
import com.okdeer.mall.order.mapper.TradeOrderMapper;
import com.okdeer.mall.order.pay.callback.AbstractPayResultHandler;
import com.okdeer.mall.order.pay.callback.PayResultHandlerFactory;
import com.okdeer.mall.order.service.OrderReturnCouponsService;

/**
 * @ClassName: AlipayStatusSubscriber
 * @Description: 订单状态写入消息,第三方支付
 * @author yangq
 * @date 2016年3月30日 下午7:39:54
 * 
 * =================================================================================================
 *     Task ID			  Date			     Author		      Description
 * ----------------+----------------+-------------------+-------------------------------------------
 *     重构4.1          2016年7月16日                              zengj			服务店订单支付回调
 *     12002           2016年8月5日                                zengj			增加服务店订单下单成功增加销量
 *     重构4.1          2016年8月16日                              zengj			支付成功回调判断订单状态是不是买家支付中
 *     重构4.1          2016年8月24日                              maojj			支付成功，如果订单是到店自提，则生成提货码
 *     重构4.1          2016年9月22日                              zhaoqc         从V1.0.0移动代码 
 *     V1.1.0          2016年9月29日                             zhaoqc         新增到店消费订单处理         
 *     V1.1.0			2016-10-15			   wushp				邀请注册首单返券        
 */
@Service
public class ThirdStatusSubscriber extends AbstractRocketMQSubscriber
		implements PayMessageConstant, OrderMessageConstant {

	private static final Logger logger = LoggerFactory.getLogger(ThirdStatusSubscriber.class);
	
	@Resource
	private PayResultHandlerFactory payResultHandlerFactory;
	
	@Resource
	private TradeOrderMapper tradeOrderMapper;
    
	@Override
	public String getTopic() {
		return TOPIC_PAY;
	}

	@Override
	public String getTags() {
		return TAG_ORDER + JOINT + TAG_POST_ORDER;
	}
	
	/**
	 * 订单返券service
	 */
	@Autowired
	private OrderReturnCouponsService orderReturnCouponsService;
	
	@Override
	public ConsumeConcurrentlyStatus subscribeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
		String tradeNum = null;
		TradeOrder tradeOrder = null;
		try {
			String msg = new String(msgs.get(0).getBody(), Charsets.UTF_8);
			logger.info("订单支付状态消息:" + msg);
			PayResponseDto respDto = JsonMapper.nonEmptyMapper().fromJson(msg, PayResponseDto.class);
			tradeNum = respDto.getTradeNum();
			if (StringUtils.isEmpty(tradeNum)) {
				return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
			}
			tradeOrder = tradeOrderMapper.selectByParamsTrade(tradeNum);
			AbstractPayResultHandler handler = payResultHandlerFactory.getByOrder(tradeOrder);
			handler.handler(tradeOrder, respDto);
		} catch (Exception e) {
			logger.error("订单支付状态消息处理失败", e);
			return ConsumeConcurrentlyStatus.RECONSUME_LATER;
		}
		
		// begin add by wushp 20161015  
		try {
			if(tradeOrder.getOrderResource() != OrderResourceEnum.SWEEP){
				//不是扫码购订单才返券
				orderReturnCouponsService.firstOrderReturnCoupons(tradeOrder);
			}
		} catch (Exception e) {
			logger.error(ExceptionConstant.COUPONS_REGISTE_RETURN_FAIL, tradeNum, e);
			return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
		}
		// end add by wushp 20161015 
		return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
	}
}
