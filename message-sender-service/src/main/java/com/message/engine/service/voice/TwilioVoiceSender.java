package com.message.engine.service.voice;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.type.PhoneNumber;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.Map;

@Component("twilio-voice")
public class TwilioVoiceSender implements VoiceSender {

    @Override
    public Mono<Void> sendVoice(Map<String, Object> config, String to, String twiml) {
        return Mono.fromRunnable(() -> {
                    String sid = config.get("accountSid").toString();
                    String token = config.get("authToken").toString();
                    String from = config.get("from").toString();

                    Twilio.init(sid, token);

                    Call.creator(
                            new PhoneNumber(to),
                            new PhoneNumber(from),
                            URI.create("http://twimlets.com/echo?Twiml=" + URI.create(twiml).toString())
                    ).create(); // ⛔ Blocking
                }).subscribeOn(Schedulers.boundedElastic()) // ✅ Offload to non-blocking thread
                .then(); // Return Mono<Void>
    }
}
