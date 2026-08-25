package com.lms.common.autoconfigure.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充：create_time / update_time / deleted
 * <p>配合 PO 继承 BaseEntity（@TableField(fill = ...)）
 */
public class BaseMetaObjectHandler implements MetaObjectHandler {

    public static final String FIELD_CREATE_TIME = "createTime";
    public static final String FIELD_UPDATE_TIME = "updateTime";
    public static final String FIELD_DELETED = "deleted";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, FIELD_CREATE_TIME, LocalDateTime.class, now);
        strictInsertFill(metaObject, FIELD_UPDATE_TIME, LocalDateTime.class, now);
        strictInsertFill(metaObject, FIELD_DELETED, Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, FIELD_UPDATE_TIME, LocalDateTime.class, LocalDateTime.now());
    }
}
