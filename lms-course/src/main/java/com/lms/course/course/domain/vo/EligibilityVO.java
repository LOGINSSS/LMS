package com.lms.course.course.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 选课资格判定结果（前端提示 / enroll / grab 预检共用）
 */
@Data
public class EligibilityVO {

    /** 是否允许选课 */
    private boolean allowed;

    /** 规则组合模式（无规则时为空） */
    private String mode;

    /** 未满足条件的说明列表（allowed=false 时给用户看） */
    private List<String> reasons = new ArrayList<>();

    /** 命中规则数（ANY 模式展示） */
    private int matched;

    /** 规则总数 */
    private int total;
}
