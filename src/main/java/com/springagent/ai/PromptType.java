package com.springagent.ai;

import lombok.Getter;

@Getter
public enum PromptType {
    Diagnosis("diagnosis"),
    Common("common");
    private String value;
    PromptType(String value){
        this.value = value;
    }

}
