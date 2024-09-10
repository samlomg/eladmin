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
package me.zhengjie.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ZipUtil;
import com.dglbc.dbassistant.base.Express;
import com.dglbc.dbassistant.dml.Select;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.domain.ColumnInfo;
import me.zhengjie.domain.GenConfig;
import me.zhengjie.domain.vo.TableInfo;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.repository.ColumnInfoRepository;
import me.zhengjie.service.GeneratorService;
import me.zhengjie.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Zheng Jie
 * @date 2019-01-02
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeneratorServiceImpl implements GeneratorService {
    private static final Logger log = LoggerFactory.getLogger(GeneratorServiceImpl.class);
    @PersistenceContext
    private EntityManager em;

    @Autowired
    @Qualifier("DataSource4Eladmin")
    private DataSource dataSource;

    private final ColumnInfoRepository columnInfoRepository;

    private final String CONFIG_MESSAGE = "请先配置生成器";

    public Connection getConnection() {

        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public PageResult<TableInfo> getTables(String name, int[] startEnd) {
        // 使用预编译防止sql注入
        String sql = "select table_name ,create_time , engine, table_collation, table_comment from information_schema.tables " +
                "where table_schema = (select database()) " +
                "and table_name like :table order by create_time desc";
        Query query = em.createNativeQuery(sql);
        query.setFirstResult(startEnd[0]);
        query.setMaxResults(startEnd[1] - startEnd[0]);
        query.setParameter("table", StringUtils.isNotBlank(name) ? ("%" + name + "%") : "%%");
        List result = query.getResultList();
        List<TableInfo> tableInfos = new ArrayList<>();
        for (Object obj : result) {
            Object[] arr = (Object[]) obj;
            tableInfos.add(new TableInfo(arr[0], arr[1], arr[2], arr[3], ObjectUtil.isNotEmpty(arr[4]) ? arr[4] : "-"));
        }
        String countSql = "select count(1) from information_schema.tables " +
                "where table_schema = (select database()) and table_name like :table";
        Query queryCount = em.createNativeQuery(countSql);
        queryCount.setParameter("table", StringUtils.isNotBlank(name) ? ("%" + name + "%") : "%%");
        BigInteger totalElements = (BigInteger) queryCount.getSingleResult();
        return PageUtil.toPage(tableInfos, totalElements.longValue());
    }

    @Override
    public PageResult<TableInfo> getTables(String name, String dataBaseId, int page, int size) {
        if (StringUtils.isEmpty(dataBaseId)) {
            //如果为空，默认是本地数据库
            // 使用预编译防止sql注入
            int[] startEnd = PageUtil.transToStartEnd(page, size);
            return getTables(name, startEnd);
        }else if (dataBaseId.equals("local")) {
            //如果为local 本地数据库
            // 使用预编译防止sql注入
            int[] startEnd = PageUtil.transToStartEnd(page, size);
            return getTables(name, startEnd);
        }else {
            List<TableInfo> tableInfos = null;
            int count = 0;
            // 1：获取 数据库资料
            Select select = Select.create().column("*")
                    .from("mnt_database", "a").where().eq("db_id", dataBaseId);
            String[] databases = new String[3];
            //查询所有的总数
            SqlUtils.queryObject(getConnection(), select.build(), rs -> {
                try {
                    databases[0] = rs.getString("jdbc_url");
                    databases[1] = rs.getString("user_name");
                    databases[2] = rs.getString("pwd");
                    return true;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });

            if (StringUtils.isEmpty(databases[0])) {
                //没有获取到数据库信息报错吧！
                throw new RuntimeException("获取数据库信息失败");
            } else {
                Express querySQL = null;
                Express countSQL = null;
                try {
                    log.info("连接数据库：{}" +databases);
                    DataSource dataSource = SqlUtils.getDataSource(databases[0], databases[1], databases[2]);
                    DatabaseMetaData md = dataSource.getConnection().getMetaData();
                    switch (md.getDatabaseProductName().toUpperCase()) {
                        case "MYSQL":
                            querySQL = tableInfoMDB().like("table_name", name).pageMySQL(size, page + 1);
                            countSQL = tableInfoMDB().like("table_name", name).buildCount();
                            break;
                        case "MARIADB":
                            querySQL = tableInfoMDB().like("table_name", name).pageMySQL(size, page + 1);
                            countSQL = tableInfoMDB().like("table_name", name).buildCount();
                            break;
                        case "MICROSOFT SQL SERVER":
                            querySQL = tableInfoMSSQL().like("A.name", name).pageSQLServerOld(size, page + 1, "A.crdate desc");
                            countSQL = tableInfoMSSQL().like("A.name", name).buildCount();
                            break;
                        default:
                            querySQL = null;
                    }
                    if (querySQL != null) {

                        tableInfos = SqlUtils.query(dataSource.getConnection(), querySQL, rs -> {
                            try {
                                return new TableInfo(rs.getString("table_name"), rs.getString("create_time"),
                                        rs.getString("engine"), rs.getString("table_collation"),
                                        rs.getString("table_comment"), dataBaseId);
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        });
                        count = SqlUtils.queryObject(dataSource.getConnection(), countSQL, rs -> {
                            try {
                                return rs.getInt(1);
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        });
                        return PageUtil.toPage(tableInfos, count);
                    }else {
                        throw new RuntimeException("数据库类型不支持");
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }

            }
        }
    }

    @Override
    public List<ColumnInfo> getColumns(String tableName) {
        List<ColumnInfo> columnInfos = columnInfoRepository.findByTableNameOrderByIdAsc(tableName);
        if (CollectionUtil.isNotEmpty(columnInfos)) {
            return columnInfos;
        } else {
            columnInfos = query(tableName);
            return columnInfoRepository.saveAll(columnInfos);
        }
    }

    @Override
    public List<ColumnInfo> getColumns(String name, String dataBaseId) {
        List<ColumnInfo> columnInfos = columnInfoRepository.findByTableNameAndDataBaseIdOrderByIdAsc(name,dataBaseId);
        if (CollectionUtil.isNotEmpty(columnInfos)) {
            return columnInfos;
        } else {
            columnInfos = query(name,dataBaseId);
            return columnInfoRepository.saveAll(columnInfos);
        }
    }

    @Override
    public List<ColumnInfo> query(String tableName) {
        // 使用预编译防止sql注入
        String sql = "select column_name, is_nullable, data_type, column_comment, column_key, extra from information_schema.columns " +
                "where table_name = ? and table_schema = (select database()) order by ordinal_position";
        Query query = em.createNativeQuery(sql);
        query.setParameter(1, tableName);
        List result = query.getResultList();
        List<ColumnInfo> columnInfos = new ArrayList<>();
        for (Object obj : result) {
            Object[] arr = (Object[]) obj;
            columnInfos.add(
                    new ColumnInfo(
                            tableName,
                            arr[0].toString(),
                            "NO".equals(arr[1]),
                            arr[2].toString(),
                            ObjectUtil.isNotNull(arr[3]) ? arr[3].toString() : null,
                            ObjectUtil.isNotNull(arr[4]) ? arr[4].toString() : null,
                            ObjectUtil.isNotNull(arr[5]) ? arr[5].toString() : null)
            );
        }
        return columnInfos;
    }

    @Override
    public List<ColumnInfo> query(String table, String dataBaseId) {
        if (StringUtils.isEmpty(dataBaseId)) {
            return query(table);
        }else if (dataBaseId.equals("local")) {
            return query(table);
        }else {
            // 1：获取 数据库资料
            Select select = Select.create().column("*")
                    .from("mnt_database", "a").where().eq("db_id", dataBaseId);
            String[] databases = new String[3];
            //查询所有的总数
            SqlUtils.queryObject(getConnection(), select.build(), rs -> {
                try {
                    databases[0] = rs.getString("jdbc_url");
                    databases[1] = rs.getString("user_name");
                    databases[2] = rs.getString("pwd");
                    return true;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            if (StringUtils.isEmpty(databases[0])) {
                //没有获取到数据库信息报错吧！
                throw new RuntimeException("获取数据库信息失败");
            } else {
                List<ColumnInfo> columnInfos = new ArrayList<>();
                Express querySQL = null;
                try {
                DataSource dataSource = SqlUtils.getDataSource(databases[0], databases[1], databases[2]);
                DatabaseMetaData md = dataSource.getConnection().getMetaData();

                    switch (md.getDatabaseProductName().toUpperCase()) {
                        case "MYSQL":
                            querySQL = tableDetailInfoMDB().eq("table_name", table).build();
                            break;
                        case "MARIADB":
                            querySQL = tableDetailInfoMDB().eq("table_name", table).build();
                            break;
                        case "MICROSOFT SQL SERVER":
                            querySQL = tableDetailInfoMSSQL().eq("d.name", table).build();
                            break;
                        default:
                            querySQL = null;
                    }
                    if (querySQL != null) {
                        return SqlUtils.query(dataSource.getConnection(), querySQL, rs -> {
                            try {
                                //column_name, is_nullable, data_type, column_comment, column_key, extra
                                return new ColumnInfo(table, rs.getString("column_name"),
                                        "NO".equals(rs.getString("is_nullable")),
                                        rs.getString("data_type"),
                                        rs.getString("column_comment"),
                                        rs.getString("column_key"),
                                        rs.getString("extra"),
                                        dataBaseId);
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }else {
                        throw new RuntimeException("数据库类型不支持");
                    }
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                }
            }
        }
        return Collections.emptyList();
    }

    @Override
    public void sync(List<ColumnInfo> columnInfos, List<ColumnInfo> columnInfoList) {
        // 第一种情况，数据库类字段改变或者新增字段
        for (ColumnInfo columnInfo : columnInfoList) {
            // 根据字段名称查找
            List<ColumnInfo> columns = columnInfos.stream().filter(c -> c.getColumnName().equals(columnInfo.getColumnName())).collect(Collectors.toList());
            // 如果能找到，就修改部分可能被字段
            if (CollectionUtil.isNotEmpty(columns)) {
                ColumnInfo column = columns.get(0);
                column.setColumnType(columnInfo.getColumnType());
                column.setExtra(columnInfo.getExtra());
                column.setKeyType(columnInfo.getKeyType());
                if (StringUtils.isBlank(column.getRemark())) {
                    column.setRemark(columnInfo.getRemark());
                }
                columnInfoRepository.save(column);
            } else {
                // 如果找不到，则保存新字段信息
                columnInfoRepository.save(columnInfo);
            }
        }
        // 第二种情况，数据库字段删除了
        for (ColumnInfo columnInfo : columnInfos) {
            // 根据字段名称查找
            List<ColumnInfo> columns = columnInfoList.stream().filter(c -> c.getColumnName().equals(columnInfo.getColumnName())).collect(Collectors.toList());
            // 如果找不到，就代表字段被删除了，则需要删除该字段
            if (CollectionUtil.isEmpty(columns)) {
                columnInfoRepository.delete(columnInfo);
            }
        }
    }

    @Override
    public void save(List<ColumnInfo> columnInfos) {
        columnInfoRepository.saveAll(columnInfos);
    }

    @Override
    public void generator(GenConfig genConfig, List<ColumnInfo> columns) {
        if (genConfig.getId() == null) {
            throw new BadRequestException(CONFIG_MESSAGE);
        }
        try {
            GenUtil.generatorCode(columns, genConfig);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new BadRequestException("生成失败，请手动处理已生成的文件");
        }
    }

    @Override
    public ResponseEntity<Object> preview(GenConfig genConfig, List<ColumnInfo> columns) {
        if (genConfig.getId() == null) {
            throw new BadRequestException(CONFIG_MESSAGE);
        }
        List<Map<String, Object>> genList = GenUtil.preview(columns, genConfig);
        return new ResponseEntity<>(genList, HttpStatus.OK);
    }

    @Override
    public void download(GenConfig genConfig, List<ColumnInfo> columns, HttpServletRequest request, HttpServletResponse response) {
        if (genConfig.getId() == null) {
            throw new BadRequestException(CONFIG_MESSAGE);
        }
        try {
            File file = new File(GenUtil.download(columns, genConfig));
            String zipPath = file.getPath() + ".zip";
            ZipUtil.zip(file.getPath(), zipPath);
            FileUtil.downloadFile(request, response, new File(zipPath), true);
        } catch (IOException e) {
            throw new BadRequestException("打包失败");
        }
    }

    /**
     * "select column_name, is_nullable, data_type, column_comment, column_key, extra from information_schema.columns " +
     * "where table_name = ? and table_schema = (select database()) order by ordinal_position";
     *
     * @return
     */
    public Select tableDetailInfoMDB() {
        return Select.create()
                .column("column_name")
                .column("is_nullable")
                .column("data_type")
                .column("column_comment")
                .column("column_key")
                .column("extra")
                .from("information_schema.columns", "A")
                .where("table_schema = (select database())")
                .last("order by ordinal_position");
    }


    /**
     * "SELECT\n" +
     * "        表名       = case when a.colorder=1 then d.name else '' end,\n" +
     * "        表说明     = case when a.colorder=1 then isnull(f.value,'') else '' end,\n" +
     * "        字段序号   = a.colorder,\n" +
     * "        column_name     = a.name,\n" +
     * "        extra       = case when COLUMNPROPERTY( a.id,a.name,'IsIdentity')=1 then 'auto_increment' else '' end,\n" +
     * "        column_key       = case when exists(SELECT 1 FROM sysobjects where xtype='PK' and parent_obj=a.id and name in (\n" +
     * "            SELECT name FROM sysindexes WHERE indid in( SELECT indid FROM sysindexkeys WHERE id = a.id AND colid=a.colid))) then 'PRI' else '' end,\n" +
     * "        data_type       = b.name,\n" +
     * "        占用字节数 = a.length,\n" +
     * "        长度       = COLUMNPROPERTY(a.id,a.name,'PRECISION'),\n" +
     * "        小数位数   = isnull(COLUMNPROPERTY(a.id,a.name,'Scale'),0),\n" +
     * "        is_nullable     = case when a.isnullable=1 then 'YES' else 'NO' end,\n" +
     * "        默认值     = isnull(e.text,''),\n" +
     * "        column_comment   = isnull(g.[value],'')\n" +
     * "FROM\n" +
     * "    syscolumns a\n" +
     * "        left join systypes b on a.xusertype=b.xusertype\n" +
     * "        inner join sysobjects d on a.id=d.id  and d.xtype='U' and  d.name<>'dtproperties'\n" +
     * "        left join syscomments e on a.cdefault=e.id\n" +
     * "        left join sys.extended_properties   g on a.id=G.major_id and a.colid=g.minor_id\n" +
     * "        left join sys.extended_properties f on d.id=f.major_id and f.minor_id=0\n" +
     * "where d.name=?\n" +
     * "order by a.id,a.colorder";
     *
     * @return
     */
    public Select tableDetailInfoMSSQL() {
        return Select.create().column("a.name", "column_name")
                .column("case when a.isnullable=1 then 'YES' else 'NO' end", "is_nullable").column("b.name", "data_type")
                .column("isnull(g.[value],'')", "column_comment")
                .column("case when exists(SELECT 1 FROM sysobjects where xtype='PK' and parent_obj=a.id and name in ( SELECT name FROM sysindexes WHERE id =a.id AND indid in( SELECT indid FROM sysindexkeys WHERE id = a.id AND colid=a.colid))) then 'PRI' else '' end", "column_key")
                .column("case when COLUMNPROPERTY( a.id,a.name,'IsIdentity')=1 then 'auto_increment' else '' end", "extra")
                .from("syscolumns", "a")
                .leftJoin(" systypes ", "b", "a.xusertype=b.xusertype ")
                .innerJoin("sysobjects", "d", "a.id=d.id  and d.xtype='U' and  d.name<>'dtproperties'")
                .leftJoin("sys.extended_properties", "g", "a.id=G.major_id and a.colid=g.minor_id")
                .where().last("order by a.id,a.colorder");
    }

    /**
     * "select A.name as table_name,A.crdate as create_time,'' as engine,'' as table_collation,B.value as table_comment \n" +
     * "FROM  sys.sysobjects A\n" +
     * "left join sys.extended_properties   B on A.id = B.major_id \n" +
     * "WHERE  A.xtype ='U' AND  A.name like ?\n"
     *
     * @return
     */
    public Select tableInfoMSSQL() {
        Select select = Select.create().column("A.name", "table_name")
                .column("A.crdate", "create_time")
                .column("''", "engine")
                .column("''", "table_collation")
                .column("B.value", "table_comment")
                .from("sys.sysobjects", "A")
                .leftJoin("sys.extended_properties", "B", "A.id = B.major_id AND B.minor_id=0 ")
                .where().eq("A.xtype", "U");
        return select;
    }

    /**
     * "select table_name ,create_time , engine, table_collation, table_comment from information_schema.tables " +
     * "where table_schema = (select database()) " +
     * "and table_name like ？ order by create_time desc"
     *
     * @return
     */
    public Select tableInfoMDB() {
        Select select = Select.create().column("table_name")
                .column("create_time")
                .column("engine")
                .column("table_collation")
                .column("table_comment")
                .from("information_schema.tables", "A")
                .where("table_schema = (select database())")
                .last("order by create_time desc");
        return select;
    }
}
