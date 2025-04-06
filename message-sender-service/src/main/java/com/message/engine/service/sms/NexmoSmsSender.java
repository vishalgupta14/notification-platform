package com.message.engine.service.sms;

import com.vonage.client.VonageClient;
import com.vonage.client.sms.messages.TextMessage;
import com.vonage.client.sms.SmsSubmissionResponse;
import com.vonage.client.sms.SmsSubmissionResponseMessage;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("nexmo")
public class NexmoSmsSender implements SmsSender {

    @Override
    public void sendSms(Map<String, Object> config, String to, String message) {
        String key = config.get("apiKey").toString();
        String secret = config.get("apiSecret").toString();
        String from = config.get("from").toString();

        VonageClient client = VonageClient.builder()
                .apiKey(key)
                .apiSecret(secret)
                .build();

        TextMessage sms = new TextMessage(from, to, message);
        SmsSubmissionResponse response = client.getSmsClient().submitMessage(sms);

        for (SmsSubmissionResponseMessage responseMessage : response.getMessages()) {
            if (!"0".equals(responseMessage.getStatus())) {
                throw new RuntimeException("Nexmo SMS failed: " + responseMessage.getErrorText());
            }
        }
    }
}
