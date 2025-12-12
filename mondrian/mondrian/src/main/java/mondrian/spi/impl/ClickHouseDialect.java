/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2021-2025 Sergei Semenkov
 */

package mondrian.spi.impl;

import mondrian.olap.Util;
import mondrian.rolap.SqlStatement;

import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

/**
 * Implementation of {@link mondrian.spi.Dialect} for ClickHouse
 */
public class ClickHouseDialect extends JdbcDialectImpl {

    public static final JdbcDialectFactory FACTORY =
            new JdbcDialectFactory(
                    ClickHouseDialect.class,
                    DatabaseProduct.CLICKHOUSE);

    /**
     * Creates a Db2OldAs400Dialect.
     *
     * @param connection Connection
     */
    public ClickHouseDialect(Connection connection) throws SQLException {
        super(connection);
    }

    public boolean requiresDrillthroughMaxRowsInLimit() {
        return true;
    }

    public void quoteStringLiteral(
            StringBuilder buf,
            String s)
    {
        buf.append('\'');

        String s0 = Util.replace(s, "\\", "\\\\");
        s0 = Util.replace(s0, "'", "\\'");
        buf.append(s0);

        buf.append('\'');
    }
    @Override
    public String toUpper(String expr) {
        return "UPPER(" + expr + ")";
    }
    @Override
    public String getDefaultUnion() {
        return "union distinct";
    }
    public boolean supportsGroupingSets() {
        return true;
    }    
    @Override
    public String generateInline(
        List<String> columnNames,
        List<String> columnTypes,
        List<String[]> valueList)
    {
        return generateInlineGeneric(
            columnNames, columnTypes, valueList, null, false);
    }    
// @Override
//     public SqlStatement.Type getType(
//         ResultSetMetaData metaData, int columnIndex)
//         throws SQLException
//     {
//         final int columnType = metaData.getColumnType(columnIndex + 1);
//         final int precision = metaData.getPrecision(columnIndex + 1);
//         final int scale = metaData.getScale(columnIndex + 1);
//         final String columnName = metaData.getColumnName(columnIndex + 1);
        
//         // Специальная обработка для GROUPING SETS, аналогично Oracle
//         // В ClickHouse при GROUPING SETS могут быть колонки с типом NUMERIC и scale=-127
//         if (columnType == Types.NUMERIC
//             && (scale == 0 || scale == -127)
//             && precision == 0 && columnName.startsWith("m"))
//         {
//             return SqlStatement.Type.OBJECT;
//         }

//         // Получаем точное имя типа ClickHouse для лучшей обработки
//         final String columnTypeName = metaData.getColumnTypeName(columnIndex + 1);
//         if (columnTypeName != null) {
//             String typeName = columnTypeName.toUpperCase();
            
//             // Целочисленные типы, которые гарантированно помещаются в int
//             // Int8, UInt8, Int16, UInt16, Int32
//             if (typeName.equals("INT8") || typeName.equals("UINT8") ||
//                 typeName.equals("INT16") || typeName.equals("UINT16") ||
//                 typeName.equals("INT32")) {
//                 return SqlStatement.Type.INT;
//             }
            
//             // UInt32: может не поместиться в int (max 4,294,967,295)
//             if (typeName.equals("UINT32")) {
//                 if (scale == 0 && precision > 0 && precision <= 9) {
//                     return SqlStatement.Type.INT;
//                 }
//                 return SqlStatement.Type.LONG;
//             }
            
//             // Большие целочисленные типы (Int64, UInt64, Int128, UInt128, Int256, UInt256)
//             if (typeName.equals("INT64") || typeName.equals("UINT64") ||
//                 typeName.contains("INT128") || typeName.contains("UINT128") ||
//                 typeName.contains("INT256") || typeName.contains("UINT256")) {
//                 // Для мер с небольшой precision можно попробовать int (риск overflow)
//                 if (columnName.startsWith("m") && scale == 0 && precision > 0 && precision <= 9) {
//                     return SqlStatement.Type.INT;
//                 }
//                 return SqlStatement.Type.LONG;
//             }
            
//             // Floating point типы
//             if (typeName.equals("FLOAT32") || typeName.equals("FLOAT64")) {
//                 return SqlStatement.Type.DOUBLE;
//             }
            
//             // Decimal типы
//             if (typeName.startsWith("DECIMAL")) {
//                 if (scale == 0 && precision > 0 && precision <= 9) {
//                     return SqlStatement.Type.INT;
//                 }
//                 // Для производительности используем DOUBLE, хотя OBJECT (BigDecimal) точнее
//                 return SqlStatement.Type.DOUBLE;
//             }
//         }

//         // Обработка по стандартным JDBC типам (фолбек)
//         SqlStatement.Type type = super.getType(metaData, columnIndex);
//         logTypeInfo(metaData, columnIndex, type);
//         return type;
//     }    
}

