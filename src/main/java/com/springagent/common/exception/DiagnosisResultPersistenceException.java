package com.springagent.common.exception;

/**
 * 结构化诊断结果在持久化或完成运行状态时发生异常。
 */
public class DiagnosisResultPersistenceException extends RuntimeException {

    public DiagnosisResultPersistenceException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
