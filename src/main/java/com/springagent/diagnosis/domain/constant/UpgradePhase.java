package com.springagent.diagnosis.domain.constant;

/**
 * 升级计划阶段，枚举名称与数据库 upgrade_plan_step_phase_chk 约束保持一致。
 */
public enum UpgradePhase {
    PREPARATION,
    BUILD,
    SOURCE_CODE,
    DATA,
    TESTING,
    DEPLOYMENT,
    ROLLBACK
}
