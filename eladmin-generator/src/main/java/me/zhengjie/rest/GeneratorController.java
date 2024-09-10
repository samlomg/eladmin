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
package me.zhengjie.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import me.zhengjie.domain.ColumnInfo;
import me.zhengjie.domain.vo.TableInfo;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.service.GenConfigService;
import me.zhengjie.service.GeneratorService;
import me.zhengjie.utils.PageResult;
import me.zhengjie.utils.PageUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * @author Zheng Jie
 * @date 2019-01-02
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/generator")
@Api(tags = "系统：代码生成管理")
public class GeneratorController {

    private final GeneratorService generatorService;
    private final GenConfigService genConfigService;

    @Value("${generator.enabled}")
    private Boolean generatorEnabled;


    @ApiOperation("查询数据库数据")
    @GetMapping(value = "/tables")
    public ResponseEntity<PageResult<TableInfo>> queryTables(@RequestParam(defaultValue = "") String name,
                                                             @RequestParam(defaultValue = "") String dataBaseId,
                                                             @RequestParam(defaultValue = "0")Integer page,
                                                             @RequestParam(defaultValue = "10")Integer size){
        return new ResponseEntity<>(generatorService.getTables(name, dataBaseId, page, size), HttpStatus.OK);
    }

    @ApiOperation("查询字段数据")
    @GetMapping(value = "/columns")
    public ResponseEntity<PageResult<ColumnInfo>> queryColumns(@RequestParam String tableName, @RequestParam(defaultValue = "local") String dataBaseId){
        List<ColumnInfo> columnInfos = generatorService.getColumns(tableName, dataBaseId);
        return new ResponseEntity<>(PageUtil.toPage(columnInfos,columnInfos.size()), HttpStatus.OK);
    }

    @ApiOperation("保存字段数据")
    @PutMapping
    public ResponseEntity<HttpStatus> saveColumn(@RequestBody List<ColumnInfo> columnInfos){
        generatorService.save(columnInfos);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @ApiOperation("同步字段数据")
    @PostMapping(value = "sync/{tables}/{dataBaseId}")
    public ResponseEntity<HttpStatus> syncColumn(@PathVariable List<String> tables, @PathVariable String dataBaseId) {
        for (String table : tables) {
            generatorService.sync(generatorService.getColumns(table, dataBaseId), generatorService.query(table, dataBaseId));
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @ApiOperation("生成代码")
    @PostMapping(value = "/{tableName}/{type}/{dataBaseId}")
    public ResponseEntity<Object> generatorCode(@PathVariable String tableName, @PathVariable Integer type, @PathVariable  String dataBaseId, HttpServletRequest request, HttpServletResponse response){
        // 注意代码生成不是一定直接可以使用，这里是给开发者带来方便。系统只是一个工具，所以不保证一定能生成代码，也不保证一定能正常运行。
        if(!generatorEnabled && type == 0){
            throw new BadRequestException("此环境不允许生成代码，请选择预览或者下载查看！");
        }
        switch (type){
            // 生成代码
            case 0: generatorService.generator(genConfigService.find(tableName,dataBaseId), generatorService.getColumns(tableName,dataBaseId));
                    break;
            // 预览
            case 1: return generatorService.preview(genConfigService.find(tableName,dataBaseId), generatorService.getColumns(tableName,dataBaseId));
            // 打包
            case 2: generatorService.download(genConfigService.find(tableName,dataBaseId), generatorService.getColumns(tableName,dataBaseId), request, response);
                    break;
            default: throw new BadRequestException("没有这个选项");
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
