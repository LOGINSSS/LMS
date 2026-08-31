package com.lms.learning.learning.enums;

import com.lms.common.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 积分类型枚举（0.2 扩展）
 *
 * 业务含义：学习过程中的积分来源分类，用于积分流水展示与对账；
 * 落库字段 points_record.type（TINYINT）。
 * 0.2 新增：READ_CHAPTER（阅读章节）、EXAM（完成章节练习/课程测验），
 * 这两类与 SIGN_IN/LEARN 均为课程内积分，发放时同步 ZINCRBY 课程积分榜 ZSET。
 */
@Getter
@AllArgsConstructor
public enum PointsType implements BaseEnum {

    /** 课程页签到奖励 */
    SIGN_IN(1, "签到", 5),

    /** 首次学习课次奖励 */
    LEARN(2, "学习", 2),

    /** 发布提问奖励 */
    QUESTION(3, "提问", 3),

    /** 回答问题奖励 */
    ANSWER(4, "回答", 5),

    /** 回答被采纳奖励（回答人） */
    ACCEPTED(5, "回答被采纳", 10),

    /** 首次阅读章节正文奖励（0.2 新增） */
    READ_CHAPTER(6, "阅读章节", 2),

    /** 完成章节练习/课程测验奖励（0.2 新增） */
    EXAM(7, "考试", 5);

    private final int value;
    private final String desc;

    /** 该类型每次发放的积分数 */
    private final int points;

    public static PointsType of(int value) {
        for (PointsType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return null;
    }
}
