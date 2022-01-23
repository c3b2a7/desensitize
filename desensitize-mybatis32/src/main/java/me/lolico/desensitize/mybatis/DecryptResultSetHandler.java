package me.lolico.desensitize.mybatis;

import me.lolico.desensitize.codec.Decoder;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.loader.ResultLoaderMap;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.FastResultSetHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/**
 * 解密ResultSet
 *
 * @author l00998
 */
public class DecryptResultSetHandler extends FastResultSetHandler {

    private static final Logger logger = LoggerFactory.getLogger(DecryptResultSetHandler.class);

    private static Method METHOD;

    private final Decoder decoder;

    static {
        Class<ResultColumnCache> clazz = ResultColumnCache.class;
        try {
            METHOD = clazz.getDeclaredMethod("getTypeHandler", Class.class, String.class);
            METHOD.setAccessible(true);
        } catch (NoSuchMethodException e) {
            logger.error("No such method: ResultColumnCache#getTypeHandler(Class,String).", e);
        }
    }

    public DecryptResultSetHandler(Executor executor, MappedStatement mappedStatement, ParameterHandler parameterHandler, ResultHandler resultHandler, BoundSql boundSql, RowBounds rowBounds, Decoder decoder) {
        super(executor, mappedStatement, parameterHandler, resultHandler, boundSql, rowBounds);
        this.decoder = decoder;
    }

    @Override
    protected boolean applyPropertyMappings(ResultSet rs, ResultMap resultMap, List<String> mappedColumnNames, MetaObject metaObject, ResultLoaderMap lazyLoader, String columnPrefix) throws SQLException {
        boolean foundValues = false;
        final List<ResultMapping> propertyMappings = resultMap.getPropertyResultMappings();
        for (ResultMapping propertyMapping : propertyMappings) {
            final String column = prependPrefix(propertyMapping.getColumn(), columnPrefix);
            if (propertyMapping.isCompositeResult() || (column != null && mappedColumnNames.contains(column.toUpperCase(Locale.ENGLISH)))) {
                Object value = getPropertyMappingValue(rs, metaObject, propertyMapping, lazyLoader, columnPrefix);
                if (value != OMIT && (value != null || configuration.isCallSettersOnNulls())) { // issue #377, call setter on nulls
                    final String property = propertyMapping.getProperty(); // issue #541 make property optional
                    if (property != null) {
                        value = decryptValue(value);
                        metaObject.setValue(property, value);
                        foundValues = true;
                    }
                }
            }
        }
        return foundValues;
    }

    @Override
    protected boolean applyAutomaticMappings(ResultSet rs, List<String> unmappedColumnNames, MetaObject metaObject, String columnPrefix, ResultColumnCache resultColumnCache) throws SQLException {
        boolean foundValues = false;
        for (String columnName : unmappedColumnNames) {
            String propertyName = columnName;
            if (columnPrefix != null && columnPrefix.length() > 0) {
                // When columnPrefix is specified,
                // ignore columns without the prefix.
                if (columnName.startsWith(columnPrefix)) {
                    propertyName = columnName.substring(columnPrefix.length());
                } else {
                    continue;
                }
            }
            final String property = metaObject.findProperty(propertyName, configuration.isMapUnderscoreToCamelCase());
            if (property != null) {
                final Class<?> propertyType = metaObject.getSetterType(property);
                if (typeHandlerRegistry.hasTypeHandler(propertyType)) {
                    final TypeHandler<?> typeHandler;
                    try {
                        typeHandler = (TypeHandler<?>) METHOD.invoke(resultColumnCache, propertyType, columnName);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        logger.error("Reflection invoke failed", e);
                        return super.applyAutomaticMappings(rs, unmappedColumnNames, metaObject, columnName, resultColumnCache);
                    }
                    Object value = typeHandler.getResult(rs, columnName);
                    if (value != null || configuration.isCallSettersOnNulls()) { // issue #377, call setter on nulls
                        value = decryptValue(value);
                        metaObject.setValue(property, value);
                        foundValues = true;
                    }
                }
            }
        }
        return foundValues;
    }

    private Object decryptValue(Object value) {
        try {
            value = decoder.decode(value.toString());
        } catch (Throwable ex) {
            logger.error("decrypt value failed", ex);
            throw ex;
        }
        return value;
    }
}
