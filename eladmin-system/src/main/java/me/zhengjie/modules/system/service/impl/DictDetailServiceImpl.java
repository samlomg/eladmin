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
package me.zhengjie.modules.system.service.impl;

import com.dglbc.dbassistant.base.Express;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import me.zhengjie.modules.mnt.domain.Database;
import me.zhengjie.modules.mnt.repository.DatabaseRepository;
import me.zhengjie.modules.system.service.dto.DictSmallDto;
import me.zhengjie.utils.PageResult;
import me.zhengjie.modules.system.domain.Dict;
import me.zhengjie.modules.system.domain.DictDetail;
import me.zhengjie.modules.system.repository.DictRepository;
import me.zhengjie.modules.system.service.dto.DictDetailQueryCriteria;
import me.zhengjie.utils.*;
import me.zhengjie.modules.system.repository.DictDetailRepository;
import me.zhengjie.modules.system.service.DictDetailService;
import me.zhengjie.modules.system.service.dto.DictDetailDto;
import me.zhengjie.modules.system.service.mapstruct.DictDetailMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
* @author Zheng Jie
* @date 2019-04-10
*/
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "dict")
public class DictDetailServiceImpl implements DictDetailService {

    private final DictRepository dictRepository;
    private final DictDetailRepository dictDetailRepository;
    private final DictDetailMapper dictDetailMapper;
    private final RedisUtils redisUtils;


    @Autowired
    private DatabaseRepository databaseRepository;

    @Autowired
    @Qualifier("DataSource4Eladmin")
    private DataSource dataSource;

    @SneakyThrows
    @Override
    public PageResult<DictDetailDto> queryAll(DictDetailQueryCriteria criteria, Pageable pageable) {
//先找出所有的dictName的类型
        Optional<Dict> options = dictRepository.findByName(criteria.getDictName());
        if (options.isPresent()) {
            //如果不是空
            Dict dict = options.get();
            if (dict.getDictType() == 1) {
                //然后找到对应 常规类型
                Page<DictDetail> page = dictDetailRepository.findAll((root, criteriaQuery, criteriaBuilder) -> QueryHelp.getPredicate(root, criteria, criteriaBuilder), pageable);
                return PageUtil.toPage(page.map(dictDetailMapper::toDto));
            } else if (dict.getDictType() == 2) {
                //外联的类型
                if (dict.getDictDatabase().equals("local")) {
                    List<DictDetailDto> dictDetailDtos =SqlUtils.query(dataSource.getConnection(),new Express(dict.getDictSql(), Collections.emptyList(),true), rs->{
                        DictDetailDto dictDetailDto = new DictDetailDto();
                        DictSmallDto dictSmall = new DictSmallDto();
                        dictSmall.setId(dict.getId());
                        dictDetailDto.setDict(dictSmall);
                        dictDetailDto.setId(1L);
                        dictDetailDto.setDictSort(0);
                        try {
                            dictDetailDto.setValue(rs.getString("value"));
                            dictDetailDto.setLabel(rs.getString("label"));
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                        return dictDetailDto;
                    });
                    if (dictDetailDtos == null || dictDetailDtos.size() == 0) {
                        return PageUtil.noData();
                    }
                    return PageUtil.toPage(dictDetailDtos, Long.valueOf(dictDetailDtos.size()));
                }
                //加载connect，然后执行sql语句
                Optional<Database> database = databaseRepository.findById(dict.getDictDatabase());
                if (database.isPresent()) {
                    DataSource dataSource = SqlUtils.getDataSource(database.get().getJdbcUrl(), database.get().getUserName(), database.get().getPwd());
                    // 返回PageResult 数据类型是：DictDetailDto
                    try {
                        List<DictDetailDto> dictDetailDtos = SqlUtils.query(dataSource.getConnection(), new Express(dict.getDictSql()), rs -> {
                            DictDetailDto dictDetailDto = new DictDetailDto();
                            DictSmallDto dictSmall = new DictSmallDto();
                            dictSmall.setId(dict.getId());
                            try {
                                dictDetailDto.setDict(dictSmall);
                                dictDetailDto.setId(1L);
                                dictDetailDto.setDictSort(0);
                                dictDetailDto.setValue(rs.getString("value"));
                                dictDetailDto.setLabel(rs.getString("label"));
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                            return dictDetailDto;
                        });

                        if (dictDetailDtos == null || dictDetailDtos.size() == 0) {
                            return PageUtil.noData();
                        }
                        return PageUtil.toPage(dictDetailDtos, Long.valueOf(dictDetailDtos.size()));
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }

                }
                return PageUtil.noData();
            } else {
                // PageResult<DictDetailDto>
                return PageUtil.noData();
            }
        } else {
            return PageUtil.noData();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(DictDetail resources) {
        dictDetailRepository.save(resources);
        // 清理缓存
        delCaches(resources);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(DictDetail resources) {
        DictDetail dictDetail = dictDetailRepository.findById(resources.getId()).orElseGet(DictDetail::new);
        ValidationUtil.isNull( dictDetail.getId(),"DictDetail","id",resources.getId());
        resources.setId(dictDetail.getId());
        dictDetailRepository.save(resources);
        // 清理缓存
        delCaches(resources);
    }

    @Override
    @Cacheable(key = "'name:' + #p0")
    public List<DictDetailDto> getDictByName(String name) {
        return dictDetailMapper.toDto(dictDetailRepository.findByDictName(name));
    }

    @Override
    public PageResult<DictDetailDto> queryAllDictByName(DictDetailQueryCriteria criteria, Pageable pageable) {
        //先找出所有的dictName的类型
        Optional<Dict> options = dictRepository.findByName(criteria.getDictName());
        if (options.isPresent()) {
            //如果不是空
            Dict dict = options.get();
            if (dict.getDictType() == 1) {
                //然后找到对应 常规类型
                Page<DictDetail> page = dictDetailRepository.findAll((root, criteriaQuery, criteriaBuilder) -> QueryHelp.getPredicate(root, criteria, criteriaBuilder), pageable);
                return PageUtil.toPage(page.map(dictDetailMapper::toDto));
            } else if (dict.getDictType() == 2) {
                //外联的类型
                // PageResult<DictDetailDto>
                //加载connect，然后执行sql语句
                Optional<Database> database = databaseRepository.findById(dict.getDictDatabase());
                if (database.isPresent()) {
                    DataSource ds = SqlUtils.getDataSource(database.get().getJdbcUrl(), database.get().getUserName(), database.get().getPwd());
                    // 返回PageResult 数据类型是：DictDetailDto
                    try {
                        List<DictDetailDto> dictDetailDtos = SqlUtils.query(ds.getConnection(), new Express(dict.getDictSql()), rs -> {
                            DictDetailDto dictDetailDto = new DictDetailDto();
                            DictSmallDto dictSmall = new DictSmallDto();
                            dictSmall.setId(dict.getId());
                            try {
                                dictDetailDto.setDict(dictSmall);
                                dictDetailDto.setId(1L);
                                dictDetailDto.setDictSort(0);
                                dictDetailDto.setValue(rs.getString("value"));
                                dictDetailDto.setLabel(rs.getString("label"));
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                            return dictDetailDto;
                        });

                        return PageUtil.toPage(dictDetailDtos, Long.valueOf(dictDetailDtos.size()));
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }

                }
                return PageUtil.noData();
            } else {
                // PageResult<DictDetailDto>
                return PageUtil.noData();
            }
        } else {
            return PageUtil.noData();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DictDetail dictDetail = dictDetailRepository.findById(id).orElseGet(DictDetail::new);
        // 清理缓存
        delCaches(dictDetail);
        dictDetailRepository.deleteById(id);
    }

    public void delCaches(DictDetail dictDetail){
        Dict dict = dictRepository.findById(dictDetail.getDict().getId()).orElseGet(Dict::new);
        redisUtils.del(CacheKey.DICT_NAME + dict.getName());
    }
}
