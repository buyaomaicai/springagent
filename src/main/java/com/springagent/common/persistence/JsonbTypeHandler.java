package com.springagent.common.persistence;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(JsonNode.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbTypeHandler extends JacksonTypeHandler {

    public JsonbTypeHandler(Class<?> type) {
        super(type);
    }

    public JsonbTypeHandler(Class<?> type, Field field) {
        super(type, field);
    }

    @Override
    public void setNonNullParameter(
            PreparedStatement preparedStatement,
            int index,
            Object parameter,
            JdbcType jdbcType) throws SQLException {
        preparedStatement.setObject(index, toJson(parameter), Types.OTHER);
    }
}
