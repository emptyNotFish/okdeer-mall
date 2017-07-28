/** 
 * @Copyright: Copyright ©2005-2020 yschome.com Inc. All rights reserved
 * @项目名称: yschome-mall 
 * @文件名称: RefundsPayStatusSubscriberServiceImpl.java 
 * @Date: 2016年3月23日 
 * 注意：本内容仅限于友门鹿公司内部传阅，禁止外泄以及用于其他的商业目的 
 */

package com.okdeer.mall.order.pay;

import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import com.alibaba.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import com.alibaba.rocketmq.common.message.MessageExt;
import com.google.common.base.Charsets;
import com.okdeer.api.pay.enums.TradeErrorEnum;
import com.okdeer.archive.goods.store.service.GoodsStoreSkuServiceApi;
import com.okdeer.archive.goods.store.service.GoodsStoreSkuServiceServiceApi;
import com.okdeer.archive.store.service.StoreInfoServiceApi;
import com.okdeer.base.common.utils.StringUtils;
import com.okdeer.base.common.utils.mapper.JsonMapper;
import com.okdeer.base.framework.mq.AbstractRocketMQSubscriber;
import com.okdeer.mall.order.bo.TradeOrderContext;
import com.okdeer.mall.order.constant.mq.OrderMessageConstant;
import com.okdeer.mall.order.constant.mq.PayMessageConstant;
import com.okdeer.mall.order.constant.text.ExceptionConstant;
import com.okdeer.mall.order.entity.TradeOrder;
import com.okdeer.mall.order.entity.TradeOrderLog;
import com.okdeer.mall.order.entity.TradeOrderRefunds;
import com.okdeer.mall.order.enums.OrderStatusEnum;
import com.okdeer.mall.order.enums.RefundsStatusEnum;
import com.okdeer.mall.order.mapper.TradeOrderItemDetailMapper;
import com.okdeer.mall.order.mapper.TradeOrderItemMapper;
import com.okdeer.mall.order.mapper.TradeOrderPayMapper;
import com.okdeer.mall.order.pay.callback.AbstractPayResultHandler;
import com.okdeer.mall.order.pay.callback.PayResultHandlerFactory;
import com.okdeer.mall.order.pay.entity.ResponseResult;
import com.okdeer.mall.order.service.OrderReturnCouponsService;
import com.okdeer.mall.order.service.TradeOrderCompleteProcessService;
import com.okdeer.mall.order.service.TradeOrderItemService;
import com.okdeer.mall.order.service.TradeOrderLogService;
import com.okdeer.mall.order.service.TradeOrderPayService;
import com.okdeer.mall.order.service.TradeOrderRefundsService;
import com.okdeer.mall.order.service.TradeOrderSendMessageService;
import com.okdeer.mall.order.service.TradeOrderServiceApi;
import com.okdeer.mall.order.service.TradeorderProcessLister;
import com.okdeer.mall.order.service.TradeorderRefundProcessLister;

/**
 * 余额支付结果消息订阅处理
 * 
 * @pr yschome-mall
 * @author guocp
 * @date 2016年3月23日 下午7:25:11
 * =================================================================================================
 *     Task ID			  Date			     Author		      Description
 * ----------------+----------------+-------------------+-------------------------------------------
 *     重构4.1          2016年7月16日                               zengj				增加服务店订单下单处理流程
 *     12002           2016年8月5日                                 zengj				增加服务店订单下单成功增加销量
 *     重构4.1          2016年8月16日                               zengj				支付成功回调判断订单状态是不是买家支付中
 *     重构4.1          2016年8月22日                               maojj				余额支付失败，将订单状态更改为待付款状态
 *     重构4.1          2016年8月24日                               maojj				余额支付成功，如果是到店自提，生成提货码
 *     重构4.1          2016年9月22日                              zhaoqc             V1.0.0移动代码
 *     V1.1.0          2016年10月5日                              zhaoqc             新增余额支付到店消费订单处理  
 *     V1.1.0			2016-10-15		   wushp				邀请注册首单返券
 *     V1.2.0 		    2016-11-14		   maojj			代码优化
 *     V2.1.0          2017年3月1日                                zhaoqc             余额支付退款时发送通知
 */
@Service
public class PayResultStatusSubscriber extends AbstractRocketMQSubscriber
		implements PayMessageConstant, OrderMessageConstant {

	private static final Logger logger = LoggerFactory.getLogger(PayResultStatusSubscriber.class);

	@Autowired
	public TradeOrderRefundsService tradeOrderRefundsService;

	@Resource
	private TradeOrderPayService tradeOrderPayService;

	@Reference(version = "1.0.0", check = false)
	private TradeOrderServiceApi tradeOrderService;

	@Resource
	private TradeOrderItemService tradeOrderItemService;

	@Resource
	private TradeOrderPayMapper tradeOrderPayMapper;

	@Resource
	private TradeOrderItemMapper tradeOrderItemMapper;

	@Reference(version = "1.0.0", check = false)
	private GoodsStoreSkuServiceServiceApi goodsStoreSkuServiceService;

	@Resource
	private TradeOrderItemDetailMapper tradeOrderItemDetailMapper;


	@Reference(version = "1.0.0", check = false)
	private StoreInfoServiceApi storeInfoService;

	// Begin 12002 add by zengj
	/**
	 * 商品信息Service
	 */
	@Reference(version = "1.0.0", check = false)
	private GoodsStoreSkuServiceApi goodsStoreSkuService;
	// End 12002 add by zengj

	// Begin 1.0.Z 增加订单操作记录Service add by zengj
	/**
	 * 订单操作记录Service
	 */
	@Resource
	private TradeOrderLogService tradeOrderLogService;
	// End 1.0.Z 增加订单操作记录Service add by zengj
    
    // begin add by wushp 20161018
 	/**
 	 * 订单返券service
 	 */
 	@Autowired
 	private OrderReturnCouponsService orderReturnCouponsService;
 	// end add by wushp 20161018
 	
 	// Begin V1.2 added by maojj 2016-11-14
 	@Resource
	private PayResultHandlerFactory payResultHandlerFactory;
 	// End V1.2 added by maojj 2016-11-14
 	
 	/**
	 * 订单完成后同步商业管理系统Service
	 */
	@Resource
	private TradeOrderCompleteProcessService tradeOrderCompleteProcessService;
    
	@Resource
    private TradeOrderSendMessageService sendMessageService;
	
	@Autowired
	@Qualifier(value="jxcSynTradeorderProcessLister")
	private TradeorderProcessLister tradeorderProcessLister;
	@Autowired
	@Qualifier(value="jxcSynTradeorderRefundProcessLister")
	private TradeorderRefundProcessLister tradeorderRefundProcessLister;
	
	@Override
	public String getTopic() {
		return TOPIC_PAY_RESULT;
	}

	@Override
	public String getTags() {
		return TAG_PAY_RESULT_INSERT + JOINT + TAG_PAY_RESULT_CANCEL + JOINT + TAG_PAY_RESULT_CONFIRM + JOINT
				+ TAG_PAY_RESULT_REFUND + JOINT + TAG_PAY_RECHARGE_ORDER_BLANCE;
	}

	@Override
	public ConsumeConcurrentlyStatus subscribeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
		MessageExt message = msgs.get(0);

		switch (message.getTags()) {
			case TAG_PAY_RESULT_INSERT:
				// 订单支付
				return insertProcessResult(message);
			case TAG_PAY_RESULT_CANCEL:
				// 订单取消
				return cancelProcessResult(message);
			case TAG_PAY_RESULT_CONFIRM:
				// 确认订单
				return confirmProcessResult(message);
			case TAG_PAY_RESULT_REFUND:
				// 订单退款
				return refundProcessResult(message);
			default:
				break;
		}
		return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
	}

	/**
	 * 订单支付消息处理
	 */
	private ConsumeConcurrentlyStatus insertProcessResult(MessageExt message) {
		String tradeNum = null;
		TradeOrder tradeOrder = null;
		try {
			String msg = new String(message.getBody(), Charsets.UTF_8);
			logger.info("订单支付状态消息:" + msg);
			ResponseResult result = JsonMapper.nonEmptyMapper().fromJson(msg, ResponseResult.class);
			if (StringUtils.isEmpty(result.getTradeNum())) {
				return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
			}
			tradeNum = result.getTradeNum();
			tradeOrder = tradeOrderService.getByTradeNum(result.getTradeNum());
			AbstractPayResultHandler handler = payResultHandlerFactory.getByOrderType(tradeOrder.getType());
			handler.handler(tradeOrder, result);
		} catch (Exception e) {
			logger.error("订单支付状态消息处理失败", e);
			return ConsumeConcurrentlyStatus.RECONSUME_LATER;
		}
		
		// begin add by wushp 20161015
		try {
			orderReturnCouponsService.firstOrderReturnCoupons(tradeOrder);
			
			//add by  zhangkeneng  和左文明对接丢消息
			TradeOrderContext tradeOrderContext = new TradeOrderContext();
			tradeOrderContext.setTradeOrder(tradeOrder);
			tradeorderProcessLister.tradeOrderStatusChange(tradeOrderContext);
		} catch (Exception e) {
			logger.error(ExceptionConstant.COUPONS_REGISTE_RETURN_FAIL, tradeNum, e);
			return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
		}
		// end add by wushp 20161015
		return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
	}

	/**
	 * 取消订单支付结果消息处理
	 */
	private ConsumeConcurrentlyStatus cancelProcessResult(MessageExt message) {
		String msg = new String(message.getBody(), Charsets.UTF_8);
		logger.info("取消订单支付结果同步消息:" + msg);
		try {
			ResponseResult result = JsonMapper.nonEmptyMapper().fromJson(msg, ResponseResult.class);
			if (StringUtils.isEmpty(result.getTradeNum())) {
				return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
			}
			if (TradeErrorEnum.TRADE_REPEAT.name().equals(result.getCode())) {
				return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
			}

			TradeOrder tradeOrder = tradeOrderService.getByTradeNum(result.getTradeNum());
			// 设置当前订单状态
			tradeOrder.setCurrentStatus(tradeOrder.getStatus());
			if (OrderStatusEnum.CANCELING != tradeOrder.getStatus()
					&& OrderStatusEnum.REFUSING != tradeOrder.getStatus()) {
				logger.info(tradeOrder.getOrderNo() + "订单状态已改变，取消订单支付结果不做处理");
				return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
			}

			if (result.getCode().equals(TradeErrorEnum.SUCCESS.getName())) {
				String operator = tradeOrder.getUserId();
				// 判断是否取消订单
				if (OrderStatusEnum.CANCELING == tradeOrder.getStatus()) {
					tradeOrder.setStatus(OrderStatusEnum.CANCELED);
				} else if (OrderStatusEnum.REFUSING == tradeOrder.getStatus()) {
					tradeOrder.setStatus(OrderStatusEnum.REFUSED);
					operator = tradeOrder.getSellerId();
				}

				// Begin 1.0.Z 增加订单操作记录 add by zengj
				tradeOrderLogService.insertSelective(new TradeOrderLog(tradeOrder.getId(), operator,
						tradeOrder.getStatus().getName(), tradeOrder.getStatus().getValue()));
				// End 1.0.Z 增加订单操作记录 add by zengj
				tradeOrderService.updateOrderStatus(tradeOrder);
			} else {
				logger.error("取消(拒收)订单退款支付失败,订单编号为：" + tradeOrder.getOrderNo() + "，问题原因" + result.getMsg());
			}
			//add by  zhangkeneng  和左文明对接丢消息
			TradeOrderContext tradeOrderContext = new TradeOrderContext();
			tradeOrderContext.setTradeOrder(tradeOrder);
			tradeOrderContext.setTradeOrderPay(tradeOrder.getTradeOrderPay());
			tradeOrderContext.setItemList(tradeOrder.getTradeOrderItem());
			tradeOrderContext.setTradeOrderLogistics(tradeOrder.getTradeOrderLogistics());
			tradeorderProcessLister.tradeOrderStatusChange(tradeOrderContext);
		} catch (Exception e) {
			logger.error("取消订单支付结果同步消息处理失败", e);
			return ConsumeConcurrentlyStatus.RECONSUME_LATER;
		}
		return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
	}

	/**
	 * 确认收货支付结果消息处理
	 */
	private ConsumeConcurrentlyStatus confirmProcessResult(MessageExt message) {
		String msg = new String(message.getBody(), Charsets.UTF_8);
		logger.info("确认收货支付结果消息:" + msg);
		try {
			ResponseResult result = JsonMapper.nonEmptyMapper().fromJson(msg, ResponseResult.class);
			if (StringUtils.isEmpty(result.getTradeNum())) {
				return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
			}
			if (TradeErrorEnum.TRADE_REPEAT.name().equals(result.getCode())) {
				return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
			}

			TradeOrder tradeOrder = tradeOrderService.getByTradeNum(result.getTradeNum());
			if (result.getCode().equals(TradeErrorEnum.SUCCESS.getName())) {
				tradeOrder.setStatus(OrderStatusEnum.HAS_BEEN_SIGNED);
				tradeOrderService.updateOrderStatus(tradeOrder);
			} else {
				logger.error("确认收货支付结果同步消息处理失败,订单编号为：" + tradeOrder.getOrderNo() + "，问题原因" + result.getMsg());
			}
		} catch (Exception e) {
			logger.error("确认收货支付结果同步消息处理失败", e);
			return ConsumeConcurrentlyStatus.RECONSUME_LATER;
		}
		return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
	}

	/**
	 * 订单退款支付结果消息处理
	 */
	private ConsumeConcurrentlyStatus refundProcessResult(MessageExt message) {
		String msg = new String(message.getBody(), Charsets.UTF_8);
		logger.info("退款支付状态消息:" + msg);
		try {
			ResponseResult result = JsonMapper.nonEmptyMapper().fromJson(msg, ResponseResult.class);
			if (result.getCode().equals(TradeErrorEnum.TRADE_REPEAT)) {
				return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
			}
			if (StringUtils.isEmpty(result.getTradeNum())) {
				logger.error("退款支付状态消息处理失败,支付流水号为空：" + msg);
				return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
			}
			TradeOrderRefunds tradeOrderRefunds = tradeOrderRefundsService.getByTradeNum(result.getTradeNum());
			if (tradeOrderRefunds == null) {
				logger.error("退款支付状态消息处理失败,支付流水号查询数据为空：" + msg);
				return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
			}

			// 返回状态为success,更新订单状态
			if (result.getCode().equals(TradeErrorEnum.SUCCESS.getName())) {
				tradeOrderRefunds.setRefundMoneyTime(new Date());
				
				if(tradeOrderRefunds.getRefundsStatus() == RefundsStatusEnum.YSC_REFUND) {
					tradeOrderRefunds.setRefundsStatus(RefundsStatusEnum.YSC_REFUND_SUCCESS);
				} else if (tradeOrderRefunds.getRefundsStatus() == RefundsStatusEnum.FORCE_SELLER_REFUND) {
					tradeOrderRefunds.setRefundsStatus(RefundsStatusEnum.FORCE_SELLER_REFUND_SUCCESS);
				} else {
					tradeOrderRefunds.setRefundsStatus(RefundsStatusEnum.REFUND_SUCCESS);
				}
				tradeOrderRefundsService.updateRefunds(tradeOrderRefunds);
				
				//Begin 便利店退款成功，向用户推送消息 added by zhaoqc
                logger.info("退款成功向用户发送通知消息");
                this.sendMessageService.tradeSendMessage(null, tradeOrderRefunds);
                //End added by zhaoqc 2017-03-1
				
				// 订单完成后同步到商业管理系统
				//tradeOrderCompleteProcessService.orderRefundsCompleteSyncToJxc(tradeOrderRefunds.getId());
			} else {
				logger.error("退款支付状态消息处理失败,退款单编号为：" + tradeOrderRefunds.getRefundNo() + "，问题原因" + result.getMsg());

			}
			
			TradeOrder tradeOrder = tradeOrderService.getByTradeNum(result.getTradeNum());
			//add by  zhangkeneng  和左文明对接丢消息
			TradeOrderContext tradeOrderContext = new TradeOrderContext();
			tradeOrderContext.setTradeOrder(tradeOrder);
			tradeOrderContext.setTradeOrderRefunds(tradeOrderRefunds);
			tradeorderRefundProcessLister.tradeOrderStatusChange(tradeOrderContext);
		} catch (Exception e) {
			logger.error("退款支付状态消息处理失败", e);
			return ConsumeConcurrentlyStatus.RECONSUME_LATER;
		}
		return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
	}
}