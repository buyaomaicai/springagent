package com.springagent.diagnosis.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.springagent.common.Constant.MessageStatus;
import com.springagent.diagnosis.entity.ChatAttachment;
import com.springagent.diagnosis.entity.ChatMessage;
import com.springagent.diagnosis.entity.DiagnosisRun;
import com.springagent.diagnosis.mapper.ChatAttachmentMapper;
import com.springagent.diagnosis.mapper.ChatMessageMapper;
import com.springagent.diagnosis.mapper.DiagnosisRunMapper;
import com.springagent.diagnosis.model.DiagnosisRunStatus;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在短事务中维护诊断运行、请求消息、响应消息和附件之间的一致性。
 *
 * <p>普通 JDBC/MyBatis 事务依赖当前线程，不能用一个 {@code @Transactional}
 * 方法包住整个 {@code Flux}：方法在返回 Flux 时事务就已经结束，而模型数据会在
 * 后续订阅时才异步产生。因此这里把创建、开始和结束拆成多个短小的同步事务，
 * 每次只覆盖一次确定的数据库状态变更。</p>
 */
@Service
@RequiredArgsConstructor
public class DiagnosisRunLifecycleService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatAttachmentMapper chatAttachmentMapper;
    private final DiagnosisRunMapper diagnosisRunMapper;

    /**
     * 原子保存本次诊断的用户消息、占位助手消息、可选附件和运行主记录。
     *
     * <p>助手消息先以 {@code PENDING} 状态创建，是因为 diagnosis_run 的
     * response_message_id 受外键约束，不能引用一条尚不存在的消息。</p>
     */
    @Transactional
    public void createRun(
            ChatMessage requestMessage,
            ChatMessage responseMessage,
            ChatAttachment attachment,
            DiagnosisRun diagnosisRun
    ) {
        requireOneRow(
                chatMessageMapper.insert(requestMessage),
                "保存诊断请求消息失败"
        );
        requireOneRow(
                chatMessageMapper.insert(responseMessage),
                "保存诊断响应占位消息失败"
        );
        if (attachment != null) {
            requireOneRow(
                    chatAttachmentMapper.insert(attachment),
                    "保存诊断附件失败"
            );
        }
        requireOneRow(
                diagnosisRunMapper.insert(diagnosisRun),
                "创建诊断运行记录失败"
        );
    }

    /**
     * 将已排队的运行切换为执行中，并同步把助手消息标记为流式输出中。
     */
    @Transactional
    public void markRunning(DiagnosisRun diagnosisRun) {
        LambdaUpdateWrapper<DiagnosisRun> runUpdate =
                Wrappers.lambdaUpdate(DiagnosisRun.class)
                        .eq(DiagnosisRun::getId, diagnosisRun.getId())
                        .eq(
                                DiagnosisRun::getStatus,
                                DiagnosisRunStatus.QUEUED.name()
                        )
                        .set(
                                DiagnosisRun::getStatus,
                                DiagnosisRunStatus.RUNNING.name()
                        )
                        .set(
                                DiagnosisRun::getStartedAt,
                                diagnosisRun.getStartedAt()
                        );
        requireOneRow(
                diagnosisRunMapper.update(null, runUpdate),
                "诊断运行不是可开始的 QUEUED 状态"
        );

        LambdaUpdateWrapper<ChatMessage> messageUpdate =
                Wrappers.lambdaUpdate(ChatMessage.class)
                        .eq(
                                ChatMessage::getId,
                                diagnosisRun.getResponseMessageId()
                        )
                        .eq(ChatMessage::getStatus, MessageStatus.PENDING)
                        .set(ChatMessage::getStatus, MessageStatus.STREAMING);
        requireOneRow(
                chatMessageMapper.update(null, messageUpdate),
                "诊断响应消息不是可开始的 PENDING 状态"
        );
    }

    /**
     * 原子保存完整响应，并把运行状态切换为成功。
     */
    @Transactional
    public void markSucceeded(
            DiagnosisRun diagnosisRun,
            String fullContent,
            OffsetDateTime completedAt
    ) {
        LambdaUpdateWrapper<DiagnosisRun> runUpdate = terminalRunUpdate(
                diagnosisRun,
                DiagnosisRunStatus.SUCCEEDED,
                completedAt
        );
        requireOneRow(
                diagnosisRunMapper.update(null, runUpdate),
                "诊断运行不是可完成的 RUNNING 状态"
        );

        updateResponseMessage(
                diagnosisRun,
                MessageStatus.COMPLETED,
                fullContent,
                null
        );
    }

    /**
     * 保存已产生的部分响应和异常详情，并把运行标记为失败。
     */
    @Transactional
    public void markFailed(
            DiagnosisRun diagnosisRun,
            String partialContent,
            String errorCode,
            String errorDetail,
            OffsetDateTime completedAt
    ) {
        LambdaUpdateWrapper<DiagnosisRun> runUpdate = terminalRunUpdate(
                diagnosisRun,
                DiagnosisRunStatus.FAILED,
                completedAt
        ).set(DiagnosisRun::getErrorCode, errorCode)
                .set(DiagnosisRun::getErrorDetail, errorDetail);
        requireOneRow(
                diagnosisRunMapper.update(null, runUpdate),
                "诊断运行不是可失败的 QUEUED 或 RUNNING 状态"
        );

        updateResponseMessage(
                diagnosisRun,
                MessageStatus.FAILED,
                partialContent,
                errorDetail
        );
    }

    /**
     * 记录客户端取消。chat_message 表没有 CANCELLED 状态，因此响应消息使用 FAILED，
     * 而更精确的取消语义由 diagnosis_run 的 CANCELLED 状态表达。
     */
    @Transactional
    public void markCancelled(
            DiagnosisRun diagnosisRun,
            String partialContent,
            String detail,
            OffsetDateTime completedAt
    ) {
        LambdaUpdateWrapper<DiagnosisRun> runUpdate = terminalRunUpdate(
                diagnosisRun,
                DiagnosisRunStatus.CANCELLED,
                completedAt
        ).set(DiagnosisRun::getErrorCode, "CLIENT_CANCELLED")
                .set(DiagnosisRun::getErrorDetail, detail);
        requireOneRow(
                diagnosisRunMapper.update(null, runUpdate),
                "诊断运行不是可取消的 QUEUED 或 RUNNING 状态"
        );

        updateResponseMessage(
                diagnosisRun,
                MessageStatus.FAILED,
                partialContent,
                detail
        );
    }

    private LambdaUpdateWrapper<DiagnosisRun> terminalRunUpdate(
            DiagnosisRun diagnosisRun,
            DiagnosisRunStatus terminalStatus,
            OffsetDateTime completedAt
    ) {
        return Wrappers.lambdaUpdate(DiagnosisRun.class)
                .eq(DiagnosisRun::getId, diagnosisRun.getId())
                .in(
                        DiagnosisRun::getStatus,
                        DiagnosisRunStatus.QUEUED.name(),
                        DiagnosisRunStatus.RUNNING.name()
                )
                .set(DiagnosisRun::getStatus, terminalStatus.name())
                .set(DiagnosisRun::getCompletedAt, completedAt);
    }

    private void updateResponseMessage(
            DiagnosisRun diagnosisRun,
            MessageStatus status,
            String content,
            String errorMessage
    ) {
        LambdaUpdateWrapper<ChatMessage> messageUpdate =
                Wrappers.lambdaUpdate(ChatMessage.class)
                        .eq(
                                ChatMessage::getId,
                                diagnosisRun.getResponseMessageId()
                        )
                        .in(
                                ChatMessage::getStatus,
                                MessageStatus.PENDING,
                                MessageStatus.STREAMING
                        )
                        .set(ChatMessage::getStatus, status)
                        .set(ChatMessage::getContent, content)
                        .set(ChatMessage::getErrorMessage, errorMessage);
        requireOneRow(
                chatMessageMapper.update(null, messageUpdate),
                "诊断响应消息不是可结束的 PENDING 或 STREAMING 状态"
        );
    }

    private void requireOneRow(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
