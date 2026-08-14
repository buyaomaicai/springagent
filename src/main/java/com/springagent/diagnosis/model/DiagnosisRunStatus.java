package com.springagent.diagnosis.model;

/**
 * 一次诊断运行的生命周期状态。
 *
 * <p>枚举名称需要与 {@code diagnosis_run_status_chk} 数据库约束保持一致，
 * 这样持久化时可以直接使用 {@link Enum#name()}，避免业务代码散落容易拼错的字符串。</p>
 */
public enum DiagnosisRunStatus {

    /** 已创建运行记录，但模型流尚未被订阅。 */
    QUEUED,

    /** 模型流已经开始执行。 */
    RUNNING,

    /** 模型正常结束，响应消息已经完整保存。 */
    SUCCEEDED,

    /** 准备或流式生成过程中发生异常。 */
    FAILED,

    /** 客户端断开连接或主动取消订阅。 */
    CANCELLED
}
