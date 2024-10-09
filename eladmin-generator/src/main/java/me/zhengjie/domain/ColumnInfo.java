/*
 *  Copyright 2019-2020 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.domain;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.zhengjie.utils.GenUtil;
import javax.persistence.*;
import java.io.Serializable;

/**
 * 列的数据信息
 * @author Zheng Jie
 * @date 2019-01-02
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "code_column_config")
public class ColumnInfo implements Serializable {

    @Id
    @Column(name = "column_id")
    @ApiModelProperty(value = "ID", hidden = true)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_name")
    @ApiModelProperty(value = "表名")
    private String tableName;

    @Column(name = "column_name")
    @ApiModelProperty(value = "数据库字段名称")
    private String columnName;

    @Column(name = "column_type")
    @ApiModelProperty(value = "数据库字段类型")
    private String columnType;

    @Column(name = "key_type")
    @ApiModelProperty(value = "数据库字段键类型")
    private String keyType;

    @ApiModelProperty(value = "字段额外的参数")
    private String extra;

    @ApiModelProperty(value = "数据库字段描述")
    private String remark;

    @Column(name = "not_null")
    @ApiModelProperty(value = "是否必填")
    private Boolean notNull;

    @Column(name = "list_show")
    @ApiModelProperty(value = "是否在列表显示")
    private Boolean listShow;

    @Column(name = "form_show")
    @ApiModelProperty(value = "是否表单显示")
    private Boolean formShow;

    @Column(name = "form_type")
    @ApiModelProperty(value = "表单类型")
    private String formType;

    @Column(name = "query_type")
    @ApiModelProperty(value = "查询 1:模糊 2：精确")
    private String queryType;

    @Column(name = "dict_name")
    @ApiModelProperty(value = "字典名称")
    private String dictName;

    @Column(name = "date_annotation")
    @ApiModelProperty(value = "日期注解")
    private String dateAnnotation;

    @Column(name = "data_base_id")
    @ApiModelProperty(value = "数据库ID")
    private String dataBaseId;

    @Column(name = "column_sort")
    @ApiModelProperty(value = "权重")
    private Integer columnSort;

    public ColumnInfo(String tableName, String columnName, Boolean notNull, String columnType, String remark, String keyType, String extra) {
        this.tableName = tableName;
        this.columnName = columnName;
        this.columnType = columnType;
        this.keyType = keyType;
        this.extra = extra;
        this.notNull = notNull;
        if(GenUtil.PK.equalsIgnoreCase(keyType) && GenUtil.EXTRA.equalsIgnoreCase(extra)){
            this.notNull = false;
        }
        this.remark = remark;
        this.listShow = true;
        this.formShow = true;
        this.columnSort = 999;
    }

    public ColumnInfo(String tableName, String columnName, Boolean notNull, String columnType, String remark, String keyType, String extra,String dataBaseId) {
        this.tableName = tableName;
        this.columnName = columnName;
        this.columnType = columnType;
        this.keyType = keyType;
        this.extra = extra;
        this.notNull = notNull;
        if(GenUtil.PK.equalsIgnoreCase(keyType) && GenUtil.EXTRA.equalsIgnoreCase(extra)){
            this.notNull = false;
        }
        this.remark = remark;
        this.listShow = true;
        this.formShow = true;
        this.dataBaseId = dataBaseId;
        this.columnSort = 999;
    }

    public String getDataBaseId() {
        return dataBaseId == null ? "local" : dataBaseId;
    }
}
