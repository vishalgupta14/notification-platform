package com.message.engine.service.sms;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("twilio")
public class TwilioSmsSender implements SmsSender {

    @Override
    public void sendSms(Map<String, Object> config, String to, String message) {
        String sid = config.get("accountSid").toString();
        String token = config.get("authToken").toString();
        String from = config.get("from").toString();

        Twilio.init(sid, token);
        Message.creator(new PhoneNumber(to), new PhoneNumber(from), message).create();
    }
}
