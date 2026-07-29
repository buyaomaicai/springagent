package com.springagent.ai.prompt;

import com.springagent.ai.PromptType;

import java.util.EnumMap;

public class DiagnosisPromptStrategy {
    private final static EnumMap<PromptType,String> strategies;
    static {
        strategies = new EnumMap<>(PromptType.class);
        strategies.put(PromptType.Diagnosis,"yi");
        strategies.put(PromptType.Common,"er");
    }
    public static String getPrompt(PromptType promptType){
        return strategies.get(promptType);
    }
}
