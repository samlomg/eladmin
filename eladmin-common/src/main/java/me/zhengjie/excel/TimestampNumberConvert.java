package me.zhengjie.excel;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;


import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TimestampNumberConvert implements Converter<Timestamp> {
    @Override
    public Class<?> supportJavaTypeKey() {
        return Timestamp.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.NUMBER;
    }


    @Override
    public Timestamp convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty,
                                       GlobalConfiguration globalConfiguration) throws Exception {
        if (cellData.getType() == CellDataTypeEnum.NUMBER) {
            BigDecimal numberValue = cellData.getNumberValue();
            if (numberValue != null) {
                Date date = org.apache.poi.ss.usermodel.DateUtil.getJavaDate(numberValue.doubleValue());
                return new Timestamp(date.getTime());
            }
        } else if (cellData.getType() == CellDataTypeEnum.STRING) {
            // 如果是字符串类型，尝试解析日期时间字符串
            String stringValue = cellData.getStringValue();
            if (stringValue != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date date = sdf.parse(stringValue);
                return new Timestamp(date.getTime());
            }
        }
        // 如果你的日期是以字符串形式存储的，可以在这里添加额外的处理逻辑
        throw new IllegalArgumentException("Unsupported date format");
    }

    @Override
    public WriteCellData<?> convertToExcelData(Timestamp value, ExcelContentProperty contentProperty, GlobalConfiguration globalConfiguration) throws Exception {
        if (value != null) {
            return new WriteCellData<>(value);
        }
        return null;
    }
}
