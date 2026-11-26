package com.speakflow.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AIConfig {
    @Value("${app.ai.provider:local}") public String provider;
    @Value("${app.ai.api-key:}") public String key;
    @Value("${app.ai.model:gpt-4.1-mini}") public String model;
    @Value("${app.ai.tts-model:tts-1}") public String ttsModel;
    @Value("${app.ai.asr-model:whisper-1}") public String asrModel;
    @Value("${app.ai.voice:alloy}") public String voice;
    @Value("${app.ai.base-url:https://api.openai.com}") public String baseUrl;
    @Value("${app.audio.directory:/app/audio}") public String audioDirectory;

    public boolean enabled() { return "openai".equalsIgnoreCase(provider) && key != null && !key.isBlank(); }
}
