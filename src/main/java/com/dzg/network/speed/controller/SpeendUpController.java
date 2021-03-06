package com.dzg.network.speed.controller;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dzg.network.speed.entity.SpeedUpEntity;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * @auther: dingzhenggang
 * @since: V1.0 2019-04-16
 */
@RestController
@RequestMapping("/speedUp")
public class SpeendUpController {

  @Autowired
  private ThreadPoolTaskScheduler threadPoolTaskScheduler;

  private final String SESSION_KEY = "SessionKey";
  private final String START_UP_URL = "https://api.cloud.189.cn/speed/startSpeedV2.action";
  private final String STOP_UP_URL = "https://api.cloud.189.cn/speed/stopSpeedV2.action";
  private final String QOS_ACCESS_URL = "family/qos/startQos.action";
  private final String UP_QOS_URL = "http://api.cloud.189.cn/family/qos/startQos.action";
  private int count = 0;
  private String qosSn = "";

  private final char[] HEX = {48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 65, 66, 67, 68, 69, 70};

  @GetMapping("/start")
  public String start(@RequestParam Map<String, Object> params) {
      
    SpeedUpEntity su = new SpeedUpEntity();
    JSONObject orig = JSONObject.parseObject(JSON.toJSONString(params));
    su = (SpeedUpEntity) JSONObject.parseObject(orig.toJSONString(),
    		SpeedUpEntity.class);
    
    if (su.getLoop() == false) {
      su.setLoop(true);
    }
    if (su.getSession() == null || su.getSecret() == null) {
    	su = new SpeedUpEntity().setSession("N")
                .setSecret("M")
                .setQosClientSn("S")
                .setClientSn("L").setLoop(true);
    } else {
      BeanUtils.copyProperties(su, su);
    }
    startSpeedUp(su);
    return "开始提速和定时提速";
  }

  public void startSpeedUp(SpeedUpEntity su) {
    try {
      if (su.getLoop()) {
	      String[] response = runExec(su.getSession(), su.getSecret());
	      printInfo("Running time " + (++count) + ", response code: " + response[0] + ", content: " + response[1]);
	      // 定时10分钟执行
	      heartBeat(su);
	      // 5分钟加速
    	  startSpeed(su.getSession(), su.getSecret(), su.getQosClientSn(), su.getClientSn());
	      SpeedHeartBeat(su);
      } else {
        String[] response = runExec(su.getSession(), su.getSecret());
        printInfo("Request sent, response code: " + response[0] + ", content: " + response[1]);
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void heartBeat(SpeedUpEntity su) {
    threadPoolTaskScheduler.schedule(new Runnable() {
      @Override
      public void run() {
        runExec(su.getSession(), su.getSecret());
      }
    }, new CronTrigger("0 0/10 * * * ?"));
  }
  
  
  public void SpeedHeartBeat(SpeedUpEntity su) {
    threadPoolTaskScheduler.schedule(new Runnable() {
      @Override
      public void run() {
    	stopSpeed(su.getSession(), su.getSecret(), su.getQosClientSn(), su.getClientSn(),qosSn);
    	try {
			Thread.sleep((int)(Math.random() * 3000));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
        startSpeed(su.getSession(), su.getSecret(), su.getQosClientSn(), su.getClientSn());
      }
    }, new CronTrigger("0 0/5 * * * ?"));
  }

  private void printInfo(String string) {
    System.out.println(getTime() + " Thread " + Thread.currentThread().getId() + " - INFO - " + string);
  }

  private String[] runExec(String session, String secret) {
    String date = syncServerDate();
    String signature = getSignatrue(QOS_ACCESS_URL, session, secret, "POST", date);
    OkHttpClient okHttpClient = new OkHttpClient();
    RequestBody formBody = new FormBody.Builder().add("prodCode", 76 + "").build();
    Request req = new Request.Builder()
            .post(formBody)
            .url(UP_QOS_URL)
            .addHeader("SessionKey", session)
            .addHeader("Signature", signature)
            .addHeader("Date", date)
            .build();
    try (Response response = okHttpClient.newCall(req).execute()) {
      String content = response.body().string();
      printInfo("心跳结果:  " + content);
      int start = content.indexOf("<message>");
      int end = content.indexOf("</message>");
      if (start != -1 && end > start) {
        return new String[]{response.code() + "", content.substring(start + 9, end)};
      } else {
        return new String[]{response.code() + "", null};
      }
    } catch (Exception e) {
      e.printStackTrace();
      return new String[]{"完犊子"};
    }
  }
  
  private String[] startSpeed(String session, String secret,String qosClientSn,String clientSn) {
	  String date = syncServerDate();
    OkHttpClient okHttpClient = new OkHttpClient();
    String signature = getSignatrue(QOS_ACCESS_URL, session, secret, "POST", date);
    RequestBody formBody = new FormBody.Builder()
    		.add("qosClientSn", qosClientSn)
    		.add("clientType", "TELEIPAD")
    		.add("version", "7.3.3")
    		.add("model", "iPad")
    		.add("osFamily", "iOS")
    		.add("osVersion", "12.1")
    		.add("clientSn", clientSn)
    		.build();
    Request req = new Request.Builder()
            .post(formBody)
            .url(START_UP_URL)
            .addHeader("SessionKey", session)
            .addHeader("Signature", signature)
            .addHeader("Date", date)
            .build();
    try (Response response = okHttpClient.newCall(req).execute()) {
      String content = response.body().string();
      System.err.println("开始加速： " + content);
      int start = content.indexOf("<qosSn>");
      int end = content.indexOf("</qosSn>");
	  qosSn = content.substring(start+7, end);
	  System.err.println("当前qosSn为： " + qosSn);
	  return new String[]{"完美"};
    } catch (Exception e) {
      e.printStackTrace();
      return new String[]{"完犊子"};
    }
  }
  
  private String[] stopSpeed(String session, String secret,String qosClientSn,String clientSn,String qosSn) {
	  String date = syncServerDate();
    OkHttpClient okHttpClient = new OkHttpClient();
    String signature = getSignatrue(QOS_ACCESS_URL, session, secret, "POST", date);
    RequestBody formBody = new FormBody.Builder()
    		.add("qosSn", qosSn)
    		.add("qosClientSn", qosClientSn)
    		.add("clientType", "TELEIPAD")
    		.add("version", "7.3.3")
    		.add("model", "iPad")
    		.add("osFamily", "iOS")
    		.add("osVersion", "12.1")
    		.add("clientSn", clientSn)
    		.build();
    Request req = new Request.Builder()
            .post(formBody)
            .url(STOP_UP_URL)
            .addHeader("SessionKey", session)
            .addHeader("Signature", signature)
            .addHeader("Date", date)
            .build();
    try (Response response = okHttpClient.newCall(req).execute()) {
      String content = response.body().string();
      System.err.println("关闭加速 ： " + content);
      return new String[]{"完美"};
    } catch (Exception e) {
      e.printStackTrace();
      return new String[]{"完犊子"};
    }
  }

  private String syncServerDate() {
    SimpleDateFormat localSimpleDateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
    Date localObject1 = new Date();
    localSimpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    String str = localSimpleDateFormat.format((Date) localObject1);
    long l1 = 16000; // 原 SystemClock.elapsedRealtime() 系统启动时间, 随便填
    long l2 = 12500; // 原 FamilyConfig.pre_elapsed_time, 上次系统启动时间, 随便填
    Date localObject2 = new Date(localObject1.getTime() + (l1 - l2));
    if (localObject2 != null) {
      try {
        localSimpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return localSimpleDateFormat.format((Date) localObject2);
      } catch (Exception localException2) {
        localException2.printStackTrace();
      }
    }
    return str;
  }

  public String getSignatrue(String accessUrl, String sessionKey, String sessionSecret, String requestMethod, String syncServerDate) {
    StringBuilder localStringBuilder1 = new StringBuilder();
    StringBuilder localStringBuilder2 = new StringBuilder();
    localStringBuilder2.append(SESSION_KEY);
    localStringBuilder2.append("=");
    localStringBuilder1.append(localStringBuilder2.toString());
    localStringBuilder1.append(sessionKey);
    localStringBuilder1.append("&Operate=");
    localStringBuilder1.append(requestMethod);
    if (accessUrl.startsWith("/")) {
      localStringBuilder1.append("&RequestURI=");
    } else {
      localStringBuilder1.append("&RequestURI=/");
    }
    localStringBuilder1.append(accessUrl);
    localStringBuilder1.append("&Date=");
    localStringBuilder1.append(syncServerDate);
    return hmacsha1(localStringBuilder1.toString(), sessionSecret);
  }

  public String hmacsha1(String paramString1, String paramString2) {
    try {
      Mac localMac = Mac.getInstance("HmacSHA1");
      localMac.init(new SecretKeySpec(paramString2.getBytes(), "HmacSHA1"));
      return toHex(localMac.doFinal(paramString1.getBytes()));
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public String toHex(byte[] paramArrayOfByte) {
    if ((paramArrayOfByte != null) && (paramArrayOfByte.length != 0)) {
      StringBuilder localStringBuilder = new StringBuilder();
      int i = 0;
      while (i < paramArrayOfByte.length) {
        localStringBuilder.append(HEX[(paramArrayOfByte[i] >> 4 & 0xF)]);
        localStringBuilder.append(HEX[(paramArrayOfByte[i] & 0xF)]);
        i += 1;
      }
      return localStringBuilder.toString();
    }
    return "";
  }

  public String getTime() {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    return sdf.format(System.currentTimeMillis());
  }
}
