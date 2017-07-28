package com.okdeer.mall.order.pay.callback;

import java.math.BigDecimal;
import java.util.Date;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.okdeer.api.pay.pay.dto.PayResponseDto;
import com.okdeer.base.common.exception.ServiceException;
import com.okdeer.base.common.utils.DateUtils;
import com.okdeer.base.common.utils.UuidUtils;
import com.okdeer.base.redis.IRedisTemplateWrapper;
import com.okdeer.mall.activity.coupons.enums.ActivityTypeEnum;
import com.okdeer.mall.order.entity.TradeOrder;
import com.okdeer.mall.order.entity.TradeOrderItem;
import com.okdeer.mall.order.enums.OrderStatusEnum;
import com.okdeer.mall.order.enums.OrderTypeEnum;
import com.okdeer.mall.order.enums.PayTypeEnum;
import com.okdeer.mall.order.service.TradeOrderItemService;
import com.okdeer.mall.order.service.TradeOrderRefundsService;
import com.okdeer.mall.risk.entity.RiskOrderRecord;
import com.okdeer.mall.risk.enums.IsPreferential;
import com.okdeer.mall.risk.enums.PayAccountType;
import com.okdeer.mall.risk.service.RiskTriggerConditions;
import com.okdeer.mcm.entity.SmsVO;
import com.okdeer.mcm.service.ISmsService;

public abstract class AbstractPhoneRechargePayHandler extends AbstractPayResultHandler {

	// 存储设备ID--用于风控记录设备号
	private static final String MALL_ORDER_DEVICEID_KEY = "MALL:ORDER:DEVICE:";

	@Resource
	private IRedisTemplateWrapper<String, String> redisTemplateWrapper;

	@Autowired
	private RiskTriggerConditions riskTriggerConditions;

	@Resource
	protected TradeOrderItemService tradeOrderItemService;

	@Resource
	protected TradeOrderRefundsService tradeOrderRefundsService;

	@Value("${okdeer.recharge.partner}")
	protected String partner;

	/**
	 * 开放平台Id
	 */
	@Value("${juhe.openId}")
	protected String openId;

	/**
	 * 话费充值appKey
	 */
	@Value("${juhe.phonefee.appKey}")
	protected String appKey;

	/**
	 * 流量充值appKey
	 */
	@Value("${juhe.dataplan.appKey}")
	protected String dataPlanKey;

	/**
	 * 话费充值充值url
	 */
	@Value("${phonefee.onlineOrder}")
	protected String submitOrderUrl;

	/**
	 * 流量套餐充值url
	 */
	@Value("${dataplan.onlineOrder}")
	protected String dataplanOrderUrl;

	/*************欧飞网充值配置*********************/
	/**
	 * ofpay.userid
	 */
	@Value("${ofpay.userid}")
	protected String userid;

	/**
	 * ofpay.userpws
	 */
	@Value("${ofpay.userpws}")
	protected String userpws;

	/**
	 * ofpay.keyStr
	 */
	@Value("${ofpay.keyStr}")
	protected String keyStr;

	/**
	 * ofpay.returl
	 */
	@Value("${ofpay.returl}")
	protected String returl;

	/**
	 * ofpay.version
	 */
	@Value("${ofpay.version}")
	protected String version;

	/**
	 * ofpay.dataplan.range
	 */
	@Value("${ofpay.dataplan.range}")
	protected String range;

	/**
	 * ofpay.dataplan.effectStartTime
	 */
	@Value("${ofpay.dataplan.effectStartTime}")
	protected String effectStartTime;

	/**
	 * ofpay.dataplan.effectTime
	 */
	@Value("${ofpay.dataplan.effectTime}")
	protected String effectTime;

	/**
	 * 充值成功短信
	 */
	@Value("${recharge.success.message}")
	protected String successMsg;

	/**
	 * 充值失败短信
	 */
	@Value("${recharge.failure.message}")
	protected String failureMsg;

	/**
	 * 短信接口
	 */
	@Autowired
	public ISmsService smsService;

	@Value("${mcm.sys.code}")
	protected String mcmSysCode;

	@Value("${mcm.sys.token}")
	protected String mcmSysToken;

	@Override
	protected boolean isConsumed(TradeOrder tradeOrder) {
		if (tradeOrder == null || tradeOrder.getStatus() == OrderStatusEnum.DROPSHIPPING
				|| tradeOrder.getStatus() == OrderStatusEnum.HAS_BEEN_SIGNED) {
			return true;
		}
		// 订单Id是否已生成支付记录
		int count = tradeOrderPayMapper.selectTradeOrderPayByOrderId(tradeOrder.getId());
		if (count > 0) {
			// 如果订单Id已生成支付记录，则标识该消息已被消费
			return true;
		}
		return false;
	}

	@Override
	protected void updateOrderStatus(TradeOrder tradeOrder) throws Exception {
		// 手机充值需要根据第三方返回结果确定是否更新订单状态，走的是独立的流程。所以该方法此处不做任何处理
	}

	@Override
	protected void sendNotifyMessage(TradeOrder tradeOrder) throws Exception {
		// 手机充值无须发送通知消息
	}

	protected void updateTradeOrderStatus(TradeOrder tradeOrder) throws Exception {
		// 修改订单状态为代发货
		tradeOrder.setStatus(OrderStatusEnum.DROPSHIPPING);
		tradeOrder.setUpdateTime(new Date());
		this.tradeOrderService.updateRechargeOrderByTradeNum(tradeOrder);
	}

	protected void updateTradeOrderStatus(TradeOrder tradeOrder, String sporderId) throws ServiceException {
		// 修改订单状态为代发货
		tradeOrder.setStatus(OrderStatusEnum.DROPSHIPPING);
		tradeOrder.setUpdateTime(new Date());
		this.tradeOrderService.updataRechargeOrderStatus(tradeOrder, sporderId);
	}

	protected SmsVO createSmsVo(String mobile, String content) {
		SmsVO smsVo = new SmsVO();
		smsVo.setId(UuidUtils.getUuid());
		smsVo.setUserId(mobile);
		smsVo.setIsTiming(0);
		smsVo.setToken(mcmSysToken);
		smsVo.setSysCode(mcmSysCode);
		smsVo.setMobile(mobile);
		smsVo.setContent(content);
		smsVo.setSmsChannelType(3);
		smsVo.setSendTime(DateUtils.formatDateTime(new Date()));
		return smsVo;
	}

	/**
	 * 执行退款流程
	 * @param tradeOrder
	 * @param tradeOrderItem
	 * @param userPhone
	 * @throws Exception   
	 * @author guocp
	 * @date 2016年11月22日
	 */
	protected void refunds(TradeOrder tradeOrder, TradeOrderItem tradeOrderItem) throws Exception {

		this.tradeOrderRefundsService.insertRechargeRefunds(tradeOrder);

		// 发送失败短信
		String content = failureMsg;
		int idx = content.indexOf("#");
		content = content.replaceFirst(String.valueOf(content.charAt(idx)), tradeOrder.getUserPhone());
		idx = content.indexOf("#");
		content = content.replaceFirst(String.valueOf(content.charAt(idx)), tradeOrderItem.getSkuName());

		SmsVO smsVo = createSmsVo(tradeOrder.getUserPhone(), content);
		this.smsService.sendSms(smsVo);
	}

	/**
	 * 判断是否触发风控
	 * @param tradeOrder   
	 * @author guocp
	 * @param respDto 
	 * @param phoneno 
	 * @return 
	 * @throws Exception 
	 * @date 2016年11月22日
	 */
	protected boolean isTrigger(TradeOrder tradeOrder, PayResponseDto respDto, String phoneno) throws Exception {

		if (tradeOrder.getType() == OrderTypeEnum.PHONE_PAY_ORDER) {
			RiskOrderRecord riskOrder = new RiskOrderRecord();
			riskOrder.setId(UuidUtils.getUuid());
			riskOrder.setCreateTime(tradeOrder.getCreateTime());
			riskOrder.setDeviceId(getDeviceId(tradeOrder.getId()));
			if (tradeOrder.getTradeOrderItem() == null || tradeOrder.getTradeOrderItem().size() <= 0) {
				throw new Exception("风控过滤异常，订单项为空");
			}
			String priceStr = tradeOrder.getTradeOrderItem().get(0).getStoreSkuId();
			riskOrder.setFacePrice(new BigDecimal(priceStr));
			riskOrder.setIsPreferential(
					tradeOrder.getActivityType() == ActivityTypeEnum.VONCHER ? IsPreferential.YES : IsPreferential.NO);
			riskOrder.setLoginName(tradeOrder.getUserPhone());
			riskOrder.setPayAccount(respDto.getAccountId());
			riskOrder.setPayAccountType(getPayType(tradeOrder.getTradeOrderPay().getPayType()));
			riskOrder.setTel(phoneno);
			return riskTriggerConditions.isTrigger(riskOrder);
		}
		return false;
	}

	/**
	 * 获取支付类型
	 * @param payType
	 * @return   
	 * @author guocp
	 * @date 2016年11月22日
	 */
	protected PayAccountType getPayType(PayTypeEnum payType) {
		if (payType == PayTypeEnum.ALIPAY) {
			return PayAccountType.ALIPAY;
		} else if (payType == PayTypeEnum.WXPAY) {
			return PayAccountType.WECHAT;
		}
		return PayAccountType.OTHER;
	}

	/**
	 * 获取设备号
	 * @param tradeOrderId
	 * @return   
	 * @author guocp
	 * @date 2016年11月22日
	 */
	protected String getDeviceId(String tradeOrderId) {
		return redisTemplateWrapper.get(MALL_ORDER_DEVICEID_KEY + tradeOrderId);
	}
}
